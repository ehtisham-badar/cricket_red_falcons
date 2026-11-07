package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.LeagueSnapshot;
import com.redfalcons.ledger.repo.LeagueSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full scorecards of every HPL match (both innings, both teams), for the Tournament tab. Each comes from the
 * match's public Crickslab scorecard page (under /match-central/, which Crickslab's robots.txt allows), whose
 * streamed data carries the innings as JSON. A finished match is read once and kept in our database; a match
 * still being played is read again at most every {@link #LIVE_REFRESH}.
 */
@Service
public class TournamentScorecards {

    private static final Logger log = LoggerFactory.getLogger(TournamentScorecards.class);
    static final Duration LIVE_REFRESH = Duration.ofMinutes(10);
    /** Never ask Crickslab more often than this, whoever is looking. */
    private static final Duration MIN_GAP = Duration.ofSeconds(3);
    private static final Pattern CHUNK = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);
    /** app.crickslab.com/matches/{id}/{uuid}/{slug}/match-summary */
    private static final Pattern SUMMARY = Pattern.compile("/matches/(\\d+)/([0-9a-fA-F-]{36})/([^/?#]+)");

    private final ObjectMapper json;
    private final LeagueFeedService feed;
    private final LeagueSnapshotRepository store;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private Instant lastFetch = Instant.EPOCH;

    public TournamentScorecards(ObjectMapper json, LeagueFeedService feed, LeagueSnapshotRepository store) {
        this.json = json;
        this.feed = feed;
        this.store = store;
    }

    /** The scorecard of league match {@code id}: stored, or read from Crickslab now. Empty when it isn't a league match. */
    @SuppressWarnings("unchecked")
    public synchronized Optional<Map<String, Object>> scorecard(long id) {
        Map<String, Object> match = ((List<Map<String, Object>>) feed.summary().getOrDefault("results", List.of())).stream()
                .filter(r -> String.valueOf(r.get("id")).equals(String.valueOf(id))).findFirst().orElse(null);
        if (match == null) return Optional.empty();
        String key = "sc-" + id;
        Optional<LeagueSnapshot> kept = store.findById(key);
        Map<String, Object> stored = kept.map(this::read).orElse(null);
        boolean finished = stored != null && Boolean.TRUE.equals(stored.get("finished"));
        boolean fresh = kept.isPresent() && Instant.parse(kept.get().getFetchedAt()).plus(LIVE_REFRESH).isAfter(Instant.now());
        if (stored != null && (finished || fresh)) return Optional.of(stored);
        URI page = scorecardPage(String.valueOf(match.getOrDefault("summaryUrl", "")));
        if (page == null) return Optional.of(stored != null ? stored : unavailable("Crickslab has no scorecard page for this match yet."));
        try {
            Thread.sleep(Math.max(0, Duration.between(Instant.now(), lastFetch.plus(MIN_GAP)).toMillis()));
            lastFetch = Instant.now();
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(page).timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "RedFalconsLedger/1.0 (+https://red-falcons-ledger.vercel.app)").header("Accept", "text/html").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            Map<String, Object> card = parse(res.body());
            card.put("source", page.toString());
            boolean done = "Completed".equalsIgnoreCase(String.valueOf(match.get("status"))) && ((List<?>) card.get("innings")).size() >= 2;
            card.put("finished", done);
            store.save(new LeagueSnapshot(key, json.writeValueAsString(card), Instant.now().toString()));
            return Optional.of(card);
        } catch (Exception e) {
            log.warn("HPL scorecard {}: could not read it from Crickslab ({}).", id, e.getMessage());
            return Optional.of(stored != null ? stored : unavailable("Couldn’t load the scorecard from Crickslab right now. Try again in a minute."));
        }
    }

    /** The public scorecard page for a match, from the summary link the league page gives. */
    static URI scorecardPage(String summaryUrl) {
        Matcher m = SUMMARY.matcher(summaryUrl == null ? "" : summaryUrl);
        if (!m.find()) return null;
        return URI.create("https://crickslab.com/match-central/match-details/" + m.group(2) + "/" + m.group(3) + "/" + m.group(1) + "/scorecard");
    }

    /** The innings in a scorecard page: batting (with how each was out), bowling, extras, fall of wickets, did not bat. */
    Map<String, Object> parse(String html) throws Exception {
        StringBuilder payload = new StringBuilder();
        Matcher m = CHUNK.matcher(html);
        while (m.find()) payload.append(json.readValue("\"" + m.group(1) + "\"", String.class));
        String s = payload.toString();
        List<Map<String, Object>> innings = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int from = 0;
        while (true) {
            int at = s.indexOf("\"battingTeam\"", from);
            if (at < 0) break;
            from = at + 13;
            JsonNode inn = objectAround(s, at);
            if (inn == null || !seen.add(inn.path("inningId").asString(""))) continue;
            innings.add(innings(inn));
        }
        if (innings.isEmpty()) throw new IllegalStateException("no innings on the page");
        innings.sort((a, b) -> Integer.compare((int) a.get("order"), (int) b.get("order")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", true);
        out.put("innings", innings);
        return out;
    }

    /** The innings object that holds the "battingTeam" key at {@code at}. */
    private JsonNode objectAround(String s, int at) {
        int depth = 0;
        for (int i = at; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) {
                    try {
                        JsonNode n = json.readerFor(JsonNode.class).without(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(s.substring(i));
                        if (n.has("battingTeam")) return n;
                    } catch (Exception ignored) { /* keep looking further out */ }
                } else depth--;
            }
        }
        return null;
    }

    private static Map<String, Object> innings(JsonNode n) {
        Map<String, Object> o = new LinkedHashMap<>();
        String title = n.path("inningTitle").asString("").replaceAll("<[^>]+>", "");
        o.put("order", title.startsWith("2") ? 2 : title.startsWith("3") ? 3 : title.startsWith("4") ? 4 : 1);
        o.put("title", title);
        o.put("team", n.path("battingTeam").path("teamName").asString(""));
        o.put("logo", n.path("battingTeam").path("teamLogo").asString(""));
        o.put("runs", n.path("totalRuns").asInt(0));
        List<String> fow = new ArrayList<>();
        n.path("fallOfWickets").forEach(f -> fow.add(f.asString("").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim()));
        o.put("wickets", fow.size());
        o.put("overs", n.path("totalOvers").asString(""));
        o.put("runRate", n.path("runRate").asString(""));
        o.put("extras", n.path("extraRuns").asInt(0));
        List<String> extras = new ArrayList<>();
        n.path("extrasRunsSummary").forEach(x -> extras.add(x.asString("")));
        o.put("extrasDetail", String.join(", ", extras));
        List<Map<String, Object>> bat = new ArrayList<>();
        n.path("battingTeam").path("battingSummary").forEach(b -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", b.path("batsmanName").asString(b.path("displayName").asString("")));
            String how = b.path("wicketSummary").path("title").asString("");
            r.put("how", how.isBlank() ? "not out" : how.replaceAll("<[^>]+>", "").trim());
            r.put("out", !how.isBlank() && !how.toLowerCase().contains("not out"));
            r.put("runs", b.path("runs").asInt(0));
            r.put("balls", b.path("deliveries").asInt(0));
            r.put("fours", b.path("fours").asInt(0));
            r.put("sixes", b.path("sixes").asInt(0));
            r.put("sr", b.path("strikeRate").asString(""));
            bat.add(r);
        });
        o.put("batting", bat);
        List<Map<String, Object>> bowl = new ArrayList<>();
        n.path("bowlingTeam").path("bowlingSummary").forEach(b -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", b.path("bowlerName").asString(b.path("displayName").asString("")));
            r.put("overs", b.path("totalOvers").asString(""));
            r.put("maidens", b.path("totalMaidensOvers").asInt(0));
            r.put("runs", b.path("totalRuns").asInt(0));
            r.put("wickets", b.path("totalWickets").asInt(0));
            r.put("econ", b.path("runRate").asString(""));
            r.put("wides", b.path("wideBalls").asInt(0));
            r.put("noBalls", b.path("noBalls").asInt(0));
            bowl.add(r);
        });
        o.put("bowling", bowl);
        o.put("bowlingTeam", n.path("bowlingTeam").path("teamName").asString(""));
        o.put("fallOfWickets", fow);
        List<String> dnb = new ArrayList<>();
        n.path("didNotPlay").forEach(p -> dnb.add(p.path("displayName").asString(p.path("name").asString(""))));
        n.path("yetToPlay").forEach(p -> dnb.add(p.path("displayName").asString(p.path("name").asString(""))));
        o.put("didNotBat", dnb);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(LeagueSnapshot row) {
        try {
            return json.readValue(row.getData(), LinkedHashMap.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> unavailable(String why) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", false);
        out.put("message", why);
        return out;
    }
}

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.LeagueSnapshot;
import com.redfalcons.ledger.repo.LeagueSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
 * Full scorecards of every HPL match (both innings, both teams), for the Tournament tab. Each finished match's
 * scorecard is read once from its public Crickslab page (under /match-central/, which Crickslab's robots.txt
 * allows) and kept in our database; the app is always served from there and never waits on Crickslab. They are
 * read right after each league fetch (Saturday 6 pm, or the admin's refresh button), and once at the first start
 * so the matches already played have theirs.
 */
@Service
public class TournamentScorecards {

    private static final Logger log = LoggerFactory.getLogger(TournamentScorecards.class);
    static final String BACKFILLED = "hplScorecardsBackfilled";
    /** Never ask Crickslab more often than this, whoever is looking. */
    private static final Duration MIN_GAP = Duration.ofSeconds(3);
    private static final Pattern CHUNK = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);
    /** app.crickslab.com/matches/{id}/{uuid}/{slug}/match-summary */
    private static final Pattern SUMMARY = Pattern.compile("/matches/(\\d+)/([0-9a-fA-F-]{36})/([^/?#]+)");

    private final ObjectMapper json;
    private final LeagueFeedService feed;
    private final LeagueSnapshotRepository store;
    /** Records that the one-time first fill has run. Null in unit tests that only parse pages. */
    private final com.redfalcons.ledger.repo.SettingRepository settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private Instant lastFetch = Instant.EPOCH;

    public TournamentScorecards(ObjectMapper json, LeagueFeedService feed, LeagueSnapshotRepository store,
                                com.redfalcons.ledger.repo.SettingRepository settings) {
        this.json = json;
        this.feed = feed;
        this.store = store;
        this.settings = settings;
    }

    /** The stored scorecard of league match {@code id} (never fetched here). Empty when it isn't a league match. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> scorecard(long id) {
        Map<String, Object> match = matches().stream().filter(r -> String.valueOf(r.get("id")).equals(String.valueOf(id))).findFirst().orElse(null);
        if (match == null) return Optional.empty();
        Map<String, Object> stored = store.findById(key(id)).map(this::read).orElse(null);
        if (stored != null) return Optional.of(stored);
        return Optional.of(unavailable(finished(match)
                ? "This scorecard is added with the Saturday 6 pm update."
                : "The scorecard is added after the match, with the Saturday 6 pm update."));
    }

    /** After each league fetch: read the scorecards of finished matches we don't have yet, once each. */
    @EventListener(LeagueRefreshed.class)
    public void afterLeagueFetch() {
        fetchMissing();
    }

    /** Once, at the first start: the matches already played get their scorecards without waiting for Saturday. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnce() {
        if (settings == null || settings.existsById(BACKFILLED)) return;
        Thread t = new Thread(() -> {
            fetchMissing();
            settings.save(new com.redfalcons.ledger.model.Setting(BACKFILLED, Instant.now().toString()));
        }, "hpl-scorecards-backfill");
        t.setDaemon(true);
        t.start();
    }

    /** Reads and stores every finished match's scorecard that isn't stored yet. Returns how many were added. */
    public synchronized int fetchMissing() {
        int added = 0;
        for (Map<String, Object> m : matches()) {
            if (!finished(m)) continue;
            long id;
            try { id = Long.parseLong(String.valueOf(m.get("id"))); } catch (NumberFormatException e) { continue; }
            if (store.existsById(key(id))) continue;
            URI page = scorecardPage(String.valueOf(m.getOrDefault("summaryUrl", "")));
            if (page == null) continue;
            try {
                Thread.sleep(Math.max(0, Duration.between(Instant.now(), lastFetch.plus(MIN_GAP)).toMillis()));
                lastFetch = Instant.now();
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(page).timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "RedFalconsLedger/1.0 (+https://red-falcons-ledger.vercel.app)").header("Accept", "text/html").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
                Map<String, Object> card = parse(res.body());
                if (((List<?>) card.get("innings")).size() < 2) continue; // not finished on Crickslab yet: next time
                card.put("source", page.toString());
                card.put("finished", true);
                store.save(new LeagueSnapshot(key(id), json.writeValueAsString(card), Instant.now().toString()));
                added++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return added;
            } catch (Exception e) {
                log.warn("HPL scorecard {}: could not read it from Crickslab ({}); it will be tried at the next fetch.", id, e.getMessage());
            }
        }
        if (added > 0) log.info("HPL scorecards: {} new scorecard{} saved.", added, added == 1 ? "" : "s");
        return added;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> matches() {
        return (List<Map<String, Object>>) feed.summary().getOrDefault("results", List.of());
    }

    private static boolean finished(Map<String, Object> m) {
        return "Completed".equalsIgnoreCase(String.valueOf(m.get("status"))) || !String.valueOf(m.getOrDefault("result", "")).isEmpty();
    }

    private static String key(long id) {
        return "sc-" + id;
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

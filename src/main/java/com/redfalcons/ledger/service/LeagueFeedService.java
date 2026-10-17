package com.redfalcons.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Hundred Premier League as published on Crickslab: points table, results and top players. The server
 * reads the public tournament page (allowed by Crickslab's robots.txt; its /api/ paths are not used) at most
 * once every {@link #REFRESH}, keeps the last good copy, and serves a compact summary to the app.
 */
@Service
public class LeagueFeedService {

    private static final Logger log = LoggerFactory.getLogger(LeagueFeedService.class);
    private static final Duration REFRESH = Duration.ofMinutes(15);
    private static final Duration RETRY_AFTER_ERROR = Duration.ofMinutes(5);
    private static final Pattern CHUNK = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);
    private static final ZoneId PAKISTAN = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter CL_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy hh:mm a", Locale.ENGLISH);

    private final ObjectMapper json;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private Map<String, Object> cached;
    private Instant fetchedAt = Instant.EPOCH;
    private Instant lastTry = Instant.EPOCH;

    public LeagueFeedService(ObjectMapper json, @Value("${ledger.hpl-url:}") String url) {
        this.json = json;
        this.url = url == null ? "" : url.trim();
    }

    /** The latest summary (fetching a fresh one when it's old), or an "unavailable" note. */
    public synchronized Map<String, Object> summary() {
        Instant now = Instant.now();
        boolean stale = cached == null || fetchedAt.plus(REFRESH).isBefore(now);
        boolean mayTry = lastTry.plus(cached == null ? RETRY_AFTER_ERROR.dividedBy(5) : RETRY_AFTER_ERROR).isBefore(now);
        if (!url.isEmpty() && stale && mayTry) {
            lastTry = now;
            try {
                cached = parse(fetch());
                fetchedAt = now;
            } catch (Exception e) {
                log.warn("HPL feed: could not refresh from Crickslab ({}); keeping the last copy.", e.getMessage());
            }
        }
        if (cached == null) return Map.of("available", false);
        Map<String, Object> out = new LinkedHashMap<>(cached);
        out.put("available", true);
        out.put("updatedAt", fetchedAt.toString());
        return out;
    }

    private String fetch() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "RedFalconsLedger/1.0 (+https://red-falcons-ledger.vercel.app)")
                .header("Accept", "text/html").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
    }

    /** Pulls the page's own data (the props of its tournament component) out of the streamed page payload. */
    Map<String, Object> parse(String html) throws Exception {
        StringBuilder payload = new StringBuilder();
        Matcher m = CHUNK.matcher(html);
        while (m.find()) payload.append(json.readValue("\"" + m.group(1) + "\"", String.class));
        int at = payload.indexOf("{\"tournamentUid\":");
        if (at < 0) throw new IllegalStateException("tournament data not found on the page");
        // Read just this one object; the page's payload carries more after it.
        JsonNode props = json.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(payload.substring(at));

        Map<String, Object> out = new LinkedHashMap<>();
        JsonNode pt = props.path("tournamentPointTableData");
        out.put("title", pt.path("title").asString("Hundred Premier League"));
        out.put("logo", pt.path("logo").asString(""));
        out.put("source", url);

        List<Map<String, Object>> table = new ArrayList<>();
        for (JsonNode round : pt.path("data")) {
            if (!round.path("teams").isArray() || round.path("teams").isEmpty()) continue;
            for (JsonNode t : round.path("teams")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("round", round.path("round_title").asString(""));
                row.put("team", t.path("title").asString(""));
                row.put("logo", t.path("logo").asString(""));
                row.put("played", t.path("totalMatches").asInt());
                row.put("won", t.path("won").asInt());
                row.put("lost", t.path("lost").asInt());
                row.put("tie", t.path("tie").asInt() + t.path("draw").asInt());
                row.put("noResult", t.path("noResult").asInt());
                row.put("points", t.path("points").asInt());
                row.put("nrr", t.path("NRR").asString(""));
                List<String> form = new ArrayList<>();
                t.path("form").forEach(f -> form.add(f.asString("")));
                row.put("form", form);
                table.add(row);
            }
        }
        out.put("pointsTable", table);

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode x : props.path("tournamentAllMatchesList").path("data")) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", x.path("id").asLong());
            r.put("title", x.path("matchTitle").asString(""));
            r.put("status", x.path("status").path("title").asString(""));
            r.put("live", x.path("isLive").asBoolean(false));
            r.put("start", pakistanTime(x.path("scheduledDateTime").asString("")));
            r.put("ground", x.path("ground").path("title").asString(""));
            r.put("teams", List.of(team(x.path("firstTeam")), team(x.path("secondTeam"))));
            r.put("result", x.path("matchResult").path("description").asString(""));
            r.put("playerOfMatch", x.path("manOfTheMatch").path("name").asString(""));
            r.put("summaryUrl", x.path("summary_view_url").asString(""));
            results.add(r);
        }
        out.put("results", results);

        Map<String, Object> leaders = new LinkedHashMap<>();
        JsonNode top = props.path("initialTopPlayerData");
        for (String[] k : new String[][] {{"batting", "battingStanding"}, {"bowling", "bowlingStanding"}, {"fielding", "fieldingStanding"}}) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode p : top.path(k[1]).path("data")) {
                if (list.size() == 5) break;
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("name", p.path("name").asString(""));
                l.put("team", p.path("team").path("title").asString(""));
                l.put("value", p.path("rank").path("value").asString(""));
                l.put("label", p.path("rank").path("title").asString(""));
                list.add(l);
            }
            leaders.put(k[0], list);
        }
        out.put("leaders", leaders);
        return out;
    }

    private static Map<String, Object> team(JsonNode t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.path("title").asString(""));
        m.put("logo", t.path("logo").asString(""));
        JsonNode inn = t.path("innings").path(0);
        m.put("runs", inn.isMissingNode() ? null : inn.path("totalRuns").asInt());
        m.put("wickets", inn.isMissingNode() ? null : inn.path("totalWickets").asInt());
        m.put("overs", inn.isMissingNode() ? "" : inn.path("totalOvers").asString(""));
        return m;
    }

    /** Crickslab's times are UTC ("Sat, 17 Oct 2026 07:45 AM"); returned as an ISO date-time in Pakistan time. */
    private static String pakistanTime(String utc) {
        try {
            return LocalDateTime.parse(utc, CL_TIME).atOffset(ZoneOffset.UTC).atZoneSameInstant(PAKISTAN).toLocalDateTime().toString();
        } catch (Exception e) {
            return "";
        }
    }
}

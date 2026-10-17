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
    /** The admin's refresh button doesn't fetch more often than this, to stay polite to Crickslab. */
    private static final Duration MIN_MANUAL_GAP = Duration.ofSeconds(10);
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
    private boolean refreshing;
    private final java.util.concurrent.ExecutorService background = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hpl-feed");
        t.setDaemon(true);
        return t;
    });

    public LeagueFeedService(ObjectMapper json, @Value("${ledger.hpl-url:}") String url) {
        this.json = json;
        this.url = url == null ? "" : url.trim();
    }

    /**
     * The latest summary, or an "unavailable" note. When the copy is old it is refreshed in the background (the
     * caller gets the current copy straight away); only the very first request waits for a fetch.
     */
    public Map<String, Object> summary() {
        Instant now = Instant.now();
        boolean first;
        synchronized (this) {
            boolean stale = cached == null || fetchedAt.plus(REFRESH).isBefore(now);
            boolean mayTry = !refreshing && lastTry.plus(cached == null ? RETRY_AFTER_ERROR.dividedBy(5) : RETRY_AFTER_ERROR).isBefore(now);
            if (url.isEmpty() || !stale || !mayTry) return snapshot();
            lastTry = now;
            refreshing = true;
            first = cached == null;
        }
        if (first) refresh(); else background.submit(() -> { refresh(); });
        synchronized (this) {
            return snapshot();
        }
    }

    /**
     * Fetches the latest page now (the admin's refresh button), unless one was fetched in the last
     * {@link #MIN_MANUAL_GAP}. Returns the summary and whether the fetch worked.
     */
    public Map<String, Object> refreshNow() {
        if (url.isEmpty()) return Map.of("available", false, "refreshed", false);
        boolean go;
        synchronized (this) {
            go = !refreshing && lastTry.plus(MIN_MANUAL_GAP).isBefore(Instant.now());
            if (go) { lastTry = Instant.now(); refreshing = true; }
        }
        boolean ok = !go || refresh();
        Map<String, Object> out;
        synchronized (this) { out = new LinkedHashMap<>(snapshot()); }
        out.put("refreshed", go && ok);
        out.put("fetchOk", ok);
        return out;
    }

    private boolean refresh() {
        try {
            Map<String, Object> fresh = parse(fetch());
            synchronized (this) {
                cached = cached == null ? fresh : newest(cached, fresh);
                fetchedAt = Instant.now();
            }
            return true;
        } catch (Exception e) {
            log.warn("HPL feed: could not refresh from Crickslab ({}); keeping the last copy.", e.getMessage());
            return false;
        } finally {
            synchronized (this) { refreshing = false; }
        }
    }

    private Map<String, Object> snapshot() {
        if (cached == null) return Map.of("available", false);
        Map<String, Object> out = new LinkedHashMap<>(cached);
        out.put("available", true);
        out.put("updatedAt", fetchedAt.toString());
        return out;
    }

    /**
     * Crickslab caches its pages and may answer with an old copy ("x-nextjs-cache: STALE") while it refreshes in
     * the background; in that case wait a moment and ask again, which returns the refreshed page.
     */
    private String fetch() throws Exception {
        HttpResponse<String> res = get();
        for (int i = 0; i < 2 && "STALE".equalsIgnoreCase(res.headers().firstValue("x-nextjs-cache").orElse("")); i++) {
            Thread.sleep(3000);
            res = get();
        }
        return res.body();
    }

    private HttpResponse<String> get() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "RedFalconsLedger/1.0 (+https://red-falcons-ledger.vercel.app)")
                .header("Accept", "text/html").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res;
    }

    /**
     * Combines the copy we had with a newly fetched one so the data never goes backwards, even if Crickslab
     * serves an older cached page: a match keeps its most advanced state (finished beats in progress, more
     * balls beats fewer), and the table and leaders are replaced only by ones with at least as many games.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> newest(Map<String, Object> old, Map<String, Object> fresh) {
        Map<String, Object> out = new LinkedHashMap<>(fresh);
        if (played((List<Map<String, Object>>) old.get("pointsTable")) > played((List<Map<String, Object>>) fresh.get("pointsTable"))) {
            out.put("pointsTable", old.get("pointsTable"));
            out.put("leaders", old.get("leaders"));
        }
        Map<Object, Map<String, Object>> oldById = new HashMap<>();
        ((List<Map<String, Object>>) old.getOrDefault("results", List.of())).forEach(r -> oldById.put(r.get("id"), r));
        List<Map<String, Object>> results = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (Map<String, Object> r : (List<Map<String, Object>>) fresh.getOrDefault("results", List.of())) {
            Map<String, Object> before = oldById.get(r.get("id"));
            results.add(before != null && progress(before) > progress(r) ? before : r);
            seen.add(r.get("id"));
        }
        // A match the new copy doesn't list (an older cached page) stays.
        oldById.forEach((id, r) -> { if (!seen.contains(id)) results.add(r); });
        results.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("start"))).reversed());
        out.put("results", results);
        return out;
    }

    private static int played(List<Map<String, Object>> table) {
        return table == null ? 0 : table.stream().mapToInt(r -> ((Number) r.getOrDefault("played", 0)).intValue()).sum();
    }

    /** How far a match has got: finished counts most, then the runs and wickets on the board. */
    @SuppressWarnings("unchecked")
    private static long progress(Map<String, Object> r) {
        long p = "Completed".equalsIgnoreCase(String.valueOf(r.get("status"))) || !String.valueOf(r.getOrDefault("result", "")).isEmpty() ? 1_000_000 : 0;
        for (Map<String, Object> t : (List<Map<String, Object>>) r.getOrDefault("teams", List.of())) {
            Object runs = t.get("runs"), wk = t.get("wickets");
            if (runs instanceof Number n) p += n.longValue() * 10;
            if (wk instanceof Number n) p += n.longValue();
        }
        return p;
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
            // Crickslab can leave isLive set after a match ends, so a completed match is never shown as live.
            String status = x.path("status").path("title").asString("");
            r.put("live", x.path("isLive").asBoolean(false) && !"Completed".equalsIgnoreCase(status));
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

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.MatchDay;
import com.redfalcons.ledger.model.MatchStat;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.repo.MatchDayRepository;
import com.redfalcons.ledger.repo.MatchStatRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The admin's refresh button also brings in the scorecard of each finished Red Falcons league match that the
 * ledger has no stats for yet: it reads the match's Crickslab page (only if that site's robots.txt allows it),
 * matches the names to the squad and fills the match's stats. Stats that are already there are never
 * overwritten, and nothing is written unless the scorecard adds up to the team's total.
 */
@Service
public class ScorecardImporter {

    private static final Logger log = LoggerFactory.getLogger(ScorecardImporter.class);
    private static final String AGENT = "RedFalconsLedger";
    private static final int MAX_PER_REFRESH = 4;
    private static final Pattern US = Pattern.compile("red\\s*falcons", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper json;
    private final LeagueFeedService feed;
    private final MatchDayRepository matches;
    private final PlayerRepository players;
    private final MatchStatRepository stats;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public ScorecardImporter(ObjectMapper json, LeagueFeedService feed, MatchDayRepository matches, PlayerRepository players,
                             MatchStatRepository stats) {
        this.json = json;
        this.feed = feed;
        this.matches = matches;
        this.players = players;
        this.stats = stats;
    }

    /** What happened for each finished Red Falcons match that has no stats yet: imported, skipped or failed, and why. */
    @SuppressWarnings("unchecked")
    public synchronized List<Map<String, Object>> importMissing() {
        List<Map<String, Object>> report = new ArrayList<>();
        Map<String, Object> summary = feed.summary();
        List<Map<String, Object>> results = (List<Map<String, Object>>) summary.getOrDefault("results", List.of());
        Map<String, List<String>> robots = new HashMap<>();
        int fetched = 0;
        for (Map<String, Object> r : results) {
            List<Map<String, Object>> teams = (List<Map<String, Object>>) r.getOrDefault("teams", List.of());
            Map<String, Object> us = teams.stream().filter(t -> US.matcher(String.valueOf(t.get("name"))).find()).findFirst().orElse(null);
            Map<String, Object> them = teams.stream().filter(t -> t != us).findFirst().orElse(null);
            if (us == null || them == null || !"Completed".equalsIgnoreCase(String.valueOf(r.get("status")))) continue;
            String label = String.valueOf(r.get("title")) + " vs " + titleCase(String.valueOf(them.get("name")));
            Optional<MatchDay> mine = ledgerMatch(String.valueOf(r.get("start")), String.valueOf(them.get("name")));
            if (mine.isEmpty()) {
                report.add(entry(label, "skipped", "No match in the ledger for that date and opponent. Add it on the Days tab first."));
                continue;
            }
            String matchId = mine.get().getId();
            if (stats.findAll().stream().anyMatch(s -> s.getMatchId().equals(matchId))) continue; // already has stats: leave them alone
            String url = String.valueOf(r.getOrDefault("summaryUrl", ""));
            if (fetched >= MAX_PER_REFRESH) { report.add(entry(label, "skipped", "Several scorecards waiting; press refresh again for the rest.")); continue; }
            fetched++;
            try {
                report.add(importOne(label, matchId, url, us, them, robots));
            } catch (Exception e) {
                log.warn("Scorecard import for {} failed: {}", label, e.getMessage());
                report.add(entry(label, "failed", "Could not read the scorecard: " + e.getMessage()));
            }
        }
        return report;
    }

    Map<String, Object> importOne(String label, String matchId, String url, Map<String, Object> us, Map<String, Object> them,
                                  Map<String, List<String>> robots) throws Exception {
        URI uri = scorecardUri(url);
        if (uri == null) return entry(label, "skipped", "This match has no scorecard page on Crickslab yet.");
        if (!allowed(uri, robots)) return entry(label, "skipped", "Crickslab's robots.txt does not allow reading scorecard pages, so none was fetched.");
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                .header("User-Agent", AGENT + "/1.0 (+https://red-falcons-ledger.vercel.app)").header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

        List<Player> squad = players.findAll();
        List<NameMatch.Squad> names = squad.stream().map(p -> new NameMatch.Squad(p.getId(), p.getName())).toList();
        java.util.function.Function<String, String> idOf = n -> {
            var m = NameMatch.match(n, names);
            return m.exact() == null ? null : m.exact().id();
        };
        ScorecardParser.Card card;
        try {
            card = ScorecardParser.read(json, res.body(), n -> idOf.apply(n) != null, num(us.get("runs"), 0), num(them.get("runs"), 0), num(them.get("wickets"), 10));
        } catch (ScorecardParser.Unreadable e) {
            Map<String, Object> out = entry(label, "failed", e.getMessage());
            if (!e.sample.isBlank()) out.put("sample", e.sample);
            return out;
        }

        Map<String, int[]> rows = new LinkedHashMap<>();
        Map<String, Boolean> out = new HashMap<>();
        Map<String, String[]> how = new HashMap<>();
        Set<String> unmatched = new LinkedHashSet<>();
        for (ScorecardParser.Bat b : card.batting()) {
            String id = idOf.apply(b.name());
            if (id == null) { unmatched.add(b.name()); continue; }
            int[] n = rows.computeIfAbsent(id, k -> new int[15]);
            n[0] += b.balls();
            n[1] += Math.max(0, b.runs() - 4 * b.fours() - 6 * b.sixes()); // twos and threes aren't on a scorecard: they count as singles
            n[4] += b.fours();
            n[5] += b.sixes();
            boolean dismissed = dismissed(b.how());
            out.put(id, dismissed);
            if (dismissed) how.put(id, dismissal(b.how()));
        }
        for (ScorecardParser.Bowl w : card.bowling()) {
            String id = idOf.apply(w.name());
            if (id == null) { unmatched.add(w.name()); continue; }
            int[] n = rows.computeIfAbsent(id, k -> new int[15]);
            n[6] += w.balls();
            n[7] += Math.max(0, w.runs() - w.wides() - w.noBalls()); // the app counts wides and no-balls apart from runs off the bat
            n[8] += w.wickets();
            n[13] += w.wides();
            n[14] += w.noBalls();
        }
        for (ScorecardParser.Bat b : card.opposition()) { // catches taken against their batters
            String[] d = dismissal(b.how());
            String catcher = "caught".equals(d[0]) ? d[2] : "";
            String id = catcher.isEmpty() ? null : idOf.apply(catcher);
            if (id != null) rows.computeIfAbsent(id, k -> new int[15])[9] += 1;
        }
        if (rows.isEmpty()) return entry(label, "failed", "The scorecard was read but none of its players are in the squad.");

        List<MatchStat> toSave = new ArrayList<>();
        rows.forEach((id, n) -> {
            MatchStat s = new MatchStat(matchId, id, n, Boolean.TRUE.equals(out.get(id)));
            String[] d = how.get(id);
            if (d != null) s.setHow(d[0], d[1], d[2], null, "");
            toSave.add(s);
        });
        stats.saveAll(toSave); // one save: all of the match's stats or none
        String msg = "Stats imported for " + rows.size() + " players."
                + (unmatched.isEmpty() ? "" : " Not in the squad, left out: " + String.join(", ", unmatched) + ".")
                + " Twos and threes count as singles; set the Playing XI and finish the match on the Days tab.";
        return entry(label, "imported", msg);
    }

    // ---------- helpers ----------

    private Optional<MatchDay> ledgerMatch(String start, String opponent) {
        if (start == null || start.length() < 10) return Optional.empty();
        String date = start.substring(0, 10);
        String want = key(opponent);
        List<MatchDay> sameDay = matches.findAll().stream().filter(m -> !m.isTest() && date.equals(m.getDate())).toList();
        Optional<MatchDay> exact = sameDay.stream().filter(m -> key(m.getOpponent()).equals(want)).findFirst();
        if (exact.isPresent()) return exact;
        return sameDay.size() == 1 && sameDay.get(0).getOpponent().isBlank() ? Optional.of(sameDay.get(0)) : Optional.empty();
    }

    private static String key(String name) {
        return NameMatch.norm(name).replace(" ", "");
    }

    /** Only Crickslab's own https pages are ever fetched. */
    static URI scorecardUri(String url) {
        try {
            URI u = URI.create(url == null ? "" : url.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(u.getScheme()) && (host.equals("crickslab.com") || host.endsWith(".crickslab.com")) ? u : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** robots.txt for the page's host: a fetch is made only when the rules for everyone (or us) allow the path. */
    private boolean allowed(URI page, Map<String, List<String>> cache) throws Exception {
        String host = page.getHost();
        List<String> rules = cache.get(host);
        if (rules == null) {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("https://" + host + "/robots.txt")).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", AGENT + "/1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) rules = List.of();
            else if (res.statusCode() != 200) throw new IllegalStateException("robots.txt: HTTP " + res.statusCode());
            else rules = robotsRules(res.body());
            cache.put(host, rules);
        }
        return pathAllowed(rules, page.getRawPath() == null || page.getRawPath().isEmpty() ? "/" : page.getRawPath());
    }

    /** The rules ("A:/path" allow, "D:/path" disallow) of the groups for "*" or this app. */
    static List<String> robotsRules(String txt) {
        List<String> out = new ArrayList<>();
        boolean applies = false, inAgents = false;
        for (String raw : txt.split("\\R")) {
            String line = raw.replaceAll("#.*", "").trim();
            int c = line.indexOf(':');
            if (c < 0) continue;
            String field = line.substring(0, c).trim().toLowerCase(Locale.ROOT), value = line.substring(c + 1).trim();
            if (field.equals("user-agent")) {
                if (!inAgents) applies = false;
                inAgents = true;
                if (value.equals("*") || value.equalsIgnoreCase(AGENT)) applies = true;
            } else {
                inAgents = false;
                if (applies && field.equals("disallow") && !value.isEmpty()) out.add("D:" + value);
                if (applies && field.equals("allow") && !value.isEmpty()) out.add("A:" + value);
            }
        }
        return out;
    }

    /** The longest matching rule wins; allow wins a tie; no matching rule means allowed. */
    static boolean pathAllowed(List<String> rules, String path) {
        int best = -1;
        boolean allow = true;
        for (String r : rules) {
            String pat = r.substring(2);
            boolean matches = pat.contains("*") || pat.endsWith("$")
                    ? Pattern.compile("^" + Pattern.quote(pat).replace("*", "\\E.*\\Q").replace("$", "\\E$\\Q")).matcher(path).find()
                    : path.startsWith(pat);
            if (matches && (pat.length() > best || (pat.length() == best && r.startsWith("A")))) {
                best = pat.length();
                allow = r.startsWith("A");
            }
        }
        return allow;
    }

    private static boolean dismissed(String how) {
        String h = how == null ? "" : how.trim().toLowerCase(Locale.ROOT);
        return !h.isEmpty() && !h.matches(".*(not out|did not bat|yet to bat|batting|retired).*");
    }

    /** {kind, bowler, fielder} from "c Ali b Hamza", "b Hamza", "lbw b Hamza", "run out (Ali)", "st Ali b Hamza". */
    static String[] dismissal(String how) {
        String h = how == null ? "" : how.trim();
        String l = h.toLowerCase(Locale.ROOT);
        String bowler = "", fielder = "", kind = "other";
        Matcher by = Pattern.compile("\\bb\\.?\\s+([^()]+)$", Pattern.CASE_INSENSITIVE).matcher(h);
        if (by.find()) bowler = by.group(1).trim();
        Matcher c = Pattern.compile("^(?:c|ct|caught)\\.?\\s+(.+?)\\s+b\\.?\\s+", Pattern.CASE_INSENSITIVE).matcher(h);
        if (l.matches("^(c|ct|caught)\\s*(&|and)\\s*b\\b.*")) { kind = "caught"; fielder = bowler; }
        else if (c.find()) { kind = "caught"; fielder = c.group(1).trim(); }
        else if (l.contains("lbw")) kind = "lbw";
        else if (l.contains("run out") || l.startsWith("runout")) {
            kind = "runout";
            Matcher f = Pattern.compile("\\(([^)]+)\\)").matcher(h);
            if (f.find()) fielder = f.group(1).split("/")[0].trim();
            bowler = "";
        } else if (l.startsWith("st ") || l.startsWith("st.") || l.startsWith("stumped")) {
            kind = "stumped";
            Matcher f = Pattern.compile("^(?:st|stumped)\\.?\\s+(.+?)\\s+b\\.?\\s+", Pattern.CASE_INSENSITIVE).matcher(h);
            if (f.find()) fielder = f.group(1).trim();
        } else if (l.contains("hit wicket")) kind = "hitwicket";
        else if (l.startsWith("b ") || l.startsWith("b.")) kind = "bowled";
        return new String[] { kind, clip(bowler), clip(fielder) };
    }

    private static String clip(String s) {
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static int num(Object o, int dflt) {
        return o instanceof Number n ? n.intValue() : dflt;
    }

    private static Map<String, Object> entry(String match, String status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("match", match);
        m.put("status", status);
        m.put("message", message);
        return m;
    }

    private static String titleCase(String n) {
        StringBuilder sb = new StringBuilder();
        for (String w : n.toLowerCase(Locale.ROOT).split("\\s+")) if (!w.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }
}

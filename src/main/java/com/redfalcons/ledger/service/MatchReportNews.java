package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A News post for each finished Red Falcons league match, written from the stored league page (no fetching):
 * the result, both scores and the player of the match. Only matches from the last few days are posted, each
 * once (a setting remembers it, so a post the admin deletes doesn't come back), and the admin can edit or
 * delete the post like any other.
 */
@Service
public class MatchReportNews {

    private static final Logger log = LoggerFactory.getLogger(MatchReportNews.class);
    private static final Pattern US = Pattern.compile("red\\s*falcons", Pattern.CASE_INSENSITIVE);
    private static final ZoneId PAKISTAN = ZoneId.of("Asia/Karachi");
    /** Only matches this recent are reported, so turning this on doesn't flood News with old matches. */
    private static final int DAYS = 3;
    static final String KEY = "matchReport:";

    private final LeagueFeedService feed;
    private final NewsService news;
    private final SettingRepository settings;

    public MatchReportNews(LeagueFeedService feed, NewsService news, SettingRepository settings) {
        this.feed = feed;
        this.news = news;
        this.settings = settings;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 10 * 60_000)
    public void scheduled() {
        try {
            postNew();
        } catch (Exception e) {
            log.warn("Match report news failed: {}", e.getMessage());
        }
    }

    /** Posts the report of every recent finished Red Falcons match that has none yet; returns how many were posted. */
    @SuppressWarnings("unchecked")
    public synchronized int postNew() {
        List<Map<String, Object>> results = (List<Map<String, Object>>) feed.summary().getOrDefault("results", List.of());
        List<Map<String, Object>> ours = results.stream().filter(MatchReportNews::isOurs)
                .sorted(Comparator.comparing(r -> String.valueOf(r.get("start")))).toList();
        LocalDate today = LocalDate.now(PAKISTAN);
        int posted = 0;
        for (Map<String, Object> r : ours) {
            if (!"Completed".equalsIgnoreCase(String.valueOf(r.get("status")))) continue;
            LocalDate day = dayOf(String.valueOf(r.get("start")));
            if (day == null || day.isBefore(today.minusDays(DAYS))) continue;
            String key = KEY + r.get("id");
            if (settings.existsById(key)) continue;
            String head = header(r);
            if (news.list().stream().anyMatch(n -> n.getBody().startsWith(head))) { // already written by hand
                settings.save(new Setting(key, today.toString()));
                continue;
            }
            String[] post = write(r, feed.summary());
            news.create(post[0], "Results", post[1], false);
            settings.save(new Setting(key, today.toString()));
            posted++;
        }
        return posted;
    }

    @SuppressWarnings("unchecked")
    private static boolean isOurs(Map<String, Object> r) {
        return ((List<Map<String, Object>>) r.getOrDefault("teams", List.of())).stream().anyMatch(t -> US.matcher(String.valueOf(t.get("name"))).find());
    }

    private static LocalDate dayOf(String start) {
        try {
            return LocalDateTime.parse(start).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    /** {title, body} in the style of the team's own match reports, for a finished match on the stored league page. */
    @SuppressWarnings("unchecked")
    static String[] write(Map<String, Object> r, Map<String, Object> summary) {
        List<Map<String, Object>> teams = (List<Map<String, Object>>) r.get("teams");
        Map<String, Object> us = teams.stream().filter(t -> US.matcher(String.valueOf(t.get("name"))).find()).findFirst().orElseThrow();
        Map<String, Object> them = teams.stream().filter(t -> t != us).findFirst().orElse(Map.of("name", "the opposition"));
        String opp = titleCase(String.valueOf(them.get("name")));
        String result = String.valueOf(r.getOrDefault("result", "")).trim();
        String low = result.toLowerCase(Locale.ROOT);
        boolean won = US.matcher(result).find() && low.contains("won");
        boolean lost = !won && low.contains("won");
        int by = low.indexOf("won by");
        String margin = by >= 0 ? result.substring(by + 7).trim() : "";
        String title = "Match report: " + (won && !margin.isEmpty() ? "We beat " + opp + " by " + margin
                : lost && !margin.isEmpty() ? opp + " beat us by " + margin : "Red Falcons vs " + opp);

        StringBuilder b = new StringBuilder(header(r)).append("\n\n");
        String shown = result.replaceAll("(?i)red falcons", "Red Falcons").replaceAll("(?i)" + Pattern.quote(String.valueOf(them.get("name"))), java.util.regex.Matcher.quoteReplacement(opp));
        b.append("Result: Red Falcons ").append(score(us)).append(" · ").append(opp).append(' ').append(score(them)).append('.');
        if (!shown.isEmpty()) b.append(' ').append(shown).append('.');
        b.append('\n');
        String pom = String.valueOf(r.getOrDefault("playerOfMatch", "")).trim().replaceAll("\\s+", " ");
        if (!pom.isEmpty()) b.append("Player of the Match: ").append(pom).append(".\n");
        String standing = standing(summary);
        if (!standing.isEmpty()) b.append('\n').append(standing).append('\n');
        b.append('\n').append(won ? "Great effort, Falcons! 🦅" : lost ? "Thanks to everyone who came and supported the team. Let's regroup at training and come back stronger. 🦅"
                : "Thanks to everyone who came and supported the team. 🦅");
        return new String[] { title, b.toString() };
    }

    /** "HPL League Stage, Match 8 · Sat 7 Nov · Punjab University". */
    static String header(Map<String, Object> r) {
        Matcher n = Pattern.compile("MATCH\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(String.valueOf(r.getOrDefault("title", "")));
        LocalDate day = dayOf(String.valueOf(r.get("start")));
        List<String> parts = new ArrayList<>();
        parts.add("HPL League Stage" + (n.find() ? ", Match " + n.group(1) : ""));
        if (day != null) parts.add(day.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + day.getDayOfMonth() + " "
                + day.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
        String ground = String.valueOf(r.getOrDefault("ground", "")).trim();
        if (!ground.isEmpty()) parts.add(ground);
        return String.join(" · ", parts);
    }

    /** "Where we stand: 6th in the HPL table (P3 W0 L3)." from the stored table, or "" when we aren't in it. */
    @SuppressWarnings("unchecked")
    private static String standing(Map<String, Object> summary) {
        List<Map<String, Object>> table = (List<Map<String, Object>>) summary.getOrDefault("pointsTable", List.of());
        for (int i = 0; i < table.size(); i++) {
            Map<String, Object> t = table.get(i);
            if (!US.matcher(String.valueOf(t.get("team"))).find()) continue;
            int rank = i + 1;
            String ord = rank % 100 >= 11 && rank % 100 <= 13 ? "th" : switch (rank % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
            return "Where we stand: " + rank + ord + " in the HPL table (P" + t.get("played") + " W" + t.get("won") + " L" + t.get("lost") + ").";
        }
        return "";
    }

    /** "103 all out (20.0 ov)" or "106/2 (13.1 ov)". */
    private static String score(Map<String, Object> t) {
        Object runs = t.get("runs"), wk = t.get("wickets");
        if (!(runs instanceof Number r)) return "did not bat";
        String overs = String.valueOf(t.getOrDefault("overs", "")).trim().replaceAll("\\.0$", "");
        String s = wk instanceof Number w && w.intValue() >= 10 ? r.intValue() + " all out" : wk instanceof Number w ? r.intValue() + "/" + w.intValue() : String.valueOf(r.intValue());
        return s + (overs.isEmpty() ? "" : " (" + overs + " overs)");
    }

    private static String titleCase(String n) {
        StringBuilder sb = new StringBuilder();
        for (String w : n.toLowerCase(Locale.ROOT).split("\\s+")) if (!w.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }
}

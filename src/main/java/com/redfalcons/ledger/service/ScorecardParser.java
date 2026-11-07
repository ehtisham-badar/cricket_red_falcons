package com.redfalcons.ledger.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a match scorecard out of a Crickslab match page. The page streams its data like the tournament page
 * does (see {@link LeagueFeedService}); the scorecard's own field names aren't published, so this looks for
 * the shape instead: a list of players with a name, runs and balls is a batting card, a list with a name,
 * overs, runs and wickets is a bowling card. Which list is ours is decided by the squad (our batters and
 * bowlers are our players), and every result is checked against the team totals before it is used, so a
 * wrong guess is rejected rather than written. When nothing fits, the failure carries a few sample objects
 * from the page so the field names can be fixed.
 */
final class ScorecardParser {

    private static final Pattern CHUNK = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);
    private static final Pattern NEXT_DATA = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern LINE_ID = Pattern.compile("^[0-9a-zA-Z]+:");

    record Bat(String name, int runs, int balls, int fours, int sixes, String how) {
    }

    record Bowl(String name, int balls, int runs, int wickets, int wides, int noBalls) {
    }

    /** {@code batting}: our batters; {@code bowling}: our bowlers; {@code opposition}: the other side's batters (for catches). */
    record Card(List<Bat> batting, List<Bowl> bowling, List<Bat> opposition) {
    }

    /** A scorecard that couldn't be read, with a few objects from the page that look like scorecard rows. */
    static final class Unreadable extends Exception {
        final String sample;

        Unreadable(String message, String sample) {
            super(message);
            this.sample = sample;
        }
    }

    private ScorecardParser() {
    }

    /**
     * @param isOurs says whether a player name is one of our squad (used to tell our lists from theirs)
     * @param ourRuns / theirRuns / theirWickets the totals from the league page, used to check the lists
     */
    static Card read(ObjectMapper json, String html, java.util.function.Predicate<String> isOurs,
                     int ourRuns, int theirRuns, int theirWickets) throws Unreadable {
        return read(roots(json, html), isOurs, ourRuns, theirRuns, theirWickets);
    }

    /** The same, from data already parsed (the match as the league page carries it). */
    static Card read(List<JsonNode> roots, java.util.function.Predicate<String> isOurs,
                     int ourRuns, int theirRuns, int theirWickets) throws Unreadable {
        List<List<Bat>> bats = new ArrayList<>();
        List<List<Bowl>> bowls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> samples = new ArrayList<>();
        for (JsonNode root : roots) walk(root, bats, bowls, seen, samples);

        List<Bat> ourBat = best(bats, isOurs, g -> g.stream().map(Bat::name).toList(), g -> g.stream().mapToInt(Bat::runs).sum(), ourRuns - 45, ourRuns);
        if (ourBat == null) {
            throw new Unreadable("Our batting card was not found on the scorecard page (" + roots.size() + " data blocks, "
                    + bats.size() + " batting lists).", String.join("\n", samples)
                    + (roots.isEmpty() ? "" : (samples.isEmpty() ? "" : "\n") + "fields: " + outline(roots.get(0), 6)));
        }
        List<Bowl> ourBowl = best(bowls, isOurs, g -> g.stream().map(Bowl::name).toList(), g -> g.stream().mapToInt(Bowl::runs).sum(), theirRuns - 30, theirRuns);
        if (ourBowl != null && ourBowl.stream().mapToInt(Bowl::wickets).sum() > theirWickets) ourBowl = null;
        List<Bat> theirs = bats.stream().filter(g -> g != ourBat && g.stream().noneMatch(b -> isOurs.test(b.name())))
                .filter(g -> { int s = g.stream().mapToInt(Bat::runs).sum(); return s <= theirRuns && s >= theirRuns - 45; })
                .max(Comparator.comparingInt(g -> g.stream().mapToInt(Bat::runs).sum())).orElse(List.of());
        return new Card(ourBat, ourBowl == null ? List.of() : ourBowl, theirs);
    }

    /** The list with the most of our players (at least two, and clearly the most) whose total is plausible. */
    private static <T> List<T> best(List<List<T>> groups, java.util.function.Predicate<String> isOurs,
                                    java.util.function.Function<List<T>, List<String>> names,
                                    java.util.function.ToIntFunction<List<T>> total, int min, int max) {
        List<T> pick = null;
        int top = 1, ties = 0;
        for (List<T> g : groups) {
            int t = total.applyAsInt(g);
            if (t > max || t < min) continue;
            int ours = (int) names.apply(g).stream().filter(isOurs).count();
            if (ours > top) { top = ours; pick = g; ties = 0; }
            else if (ours == top && pick != null && !names.apply(pick).equals(names.apply(g))) ties++;
        }
        return ties > 0 ? null : pick;
    }

    /** The field names of a JSON value, nested, with the first item of each list: {id,firstTeam:{title,innings:[{totalRuns}]x1}}. */
    static String outline(JsonNode n, int depth) {
        String s = outlineOf(n, depth);
        return s.length() > 1500 ? s.substring(0, 1500) + "…" : s;
    }

    private static String outlineOf(JsonNode n, int depth) {
        if (n == null) return "";
        if (n.isObject()) {
            if (depth <= 0) return "{…}";
            StringBuilder b = new StringBuilder("{");
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                if (b.length() > 1) b.append(',');
                b.append(e.getKey());
                if (e.getValue().isObject() || e.getValue().isArray()) b.append(':').append(outlineOf(e.getValue(), depth - 1));
            }
            return b.append('}').toString();
        }
        if (n.isArray()) return n.isEmpty() ? "[]" : "[" + outlineOf(n.get(0), depth - 1) + "]x" + n.size();
        return "";
    }

    // ---------- the page's data ----------

    static List<JsonNode> roots(ObjectMapper json, String html) {
        List<JsonNode> out = new ArrayList<>();
        String body = html == null ? "" : html;
        StringBuilder payload = new StringBuilder();
        Matcher m = CHUNK.matcher(body);
        while (m.find()) {
            try { payload.append(json.readValue("\"" + m.group(1) + "\"", String.class)); } catch (Exception ignored) { /* skip a bad chunk */ }
        }
        for (String line : payload.toString().split("\n")) {
            String t = LINE_ID.matcher(line).replaceFirst("").trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try { out.add(json.readTree(t)); } catch (Exception ignored) { /* not JSON (a component reference or text) */ }
            }
        }
        Matcher n = NEXT_DATA.matcher(body);
        while (n.find()) {
            try { out.add(json.readTree(n.group(1))); } catch (Exception ignored) { /* skip */ }
        }
        String trimmed = body.trim();
        if (out.isEmpty() && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
            try { out.add(json.readTree(trimmed)); } catch (Exception ignored) { /* skip */ }
        }
        return out;
    }

    private static void walk(JsonNode node, List<List<Bat>> bats, List<List<Bowl>> bowls, Set<String> seen, List<String> samples) {
        if (node == null) return;
        if (node.isArray()) {
            List<Bat> b = new ArrayList<>();
            List<Bowl> w = new ArrayList<>();
            for (JsonNode e : node) {
                if (!e.isObject()) continue;
                Bowl bw = bowl(e);
                if (bw != null) { w.add(bw); continue; }
                Bat bt = bat(e);
                if (bt != null) b.add(bt);
            }
            if (b.size() >= 2 && seen.add("b" + b)) bats.add(b);
            if (w.size() >= 2 && seen.add("w" + w)) bowls.add(w);
            for (JsonNode e : node) walk(e, bats, bowls, seen, samples);
        } else if (node.isObject()) {
            if (samples.size() < 3 && hasKey(node, "runs") && node.size() <= 40) {
                String s = node.toString();
                samples.add(s.length() > 500 ? s.substring(0, 500) + "…" : s);
            }
            for (Map.Entry<String, JsonNode> e : node.properties()) walk(e.getValue(), bats, bowls, seen, samples);
        }
    }

    // ---------- one row ----------

    private static final List<String> NAME = List.of("playername", "name", "batsmanname", "batsman", "batter", "bowlername", "bowler", "player", "fullname", "title");
    private static final List<String> RUNS = List.of("runs", "run", "batruns", "runsscored", "totalruns", "runsconceded", "conceded");
    private static final List<String> BALLS = List.of("balls", "ballsfaced", "ball", "ballfaced", "faced");
    private static final List<String> FOURS = List.of("fours", "four", "4s", "numberoffours", "noof4s");
    private static final List<String> SIXES = List.of("sixes", "six", "6s", "numberofsixes", "noof6s");
    private static final List<String> OVERS = List.of("overs", "oversbowled", "over");
    private static final List<String> WICKETS = List.of("wickets", "wicket", "wkts", "wickettaken");
    private static final List<String> WIDES = List.of("wides", "wide", "wd");
    private static final List<String> NOBALLS = List.of("noballs", "noball", "nb");
    private static final List<String> HOW = List.of("howout", "dismissal", "outstr", "outdescription", "wicketdescription", "dismissaltext", "outby", "outtype", "status");

    private static Map<String, JsonNode> keys(JsonNode o) {
        Map<String, JsonNode> m = new HashMap<>();
        for (Map.Entry<String, JsonNode> e : o.properties()) m.put(e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), e.getValue());
        return m;
    }

    private static boolean hasKey(JsonNode o, String key) {
        return keys(o).containsKey(key);
    }

    private static JsonNode first(Map<String, JsonNode> m, List<String> aliases) {
        for (String a : aliases) if (m.containsKey(a) && !m.get(a).isNull()) return m.get(a);
        return null;
    }

    private static Integer integer(JsonNode n) {
        if (n == null) return null;
        if (n.isNumber()) return n.asInt();
        if (n.isString() && n.asString("").trim().matches("\\d+")) return Integer.parseInt(n.asString("").trim());
        return null;
    }

    private static String text(JsonNode n) {
        if (n == null) return "";
        if (n.isString()) return n.asString("").trim();
        if (n.isObject()) {
            Map<String, JsonNode> k = keys(n);
            JsonNode inner = first(k, List.of("name", "fullname", "title", "playername"));
            return inner != null && inner.isString() ? inner.asString("").trim() : "";
        }
        return "";
    }

    /** Overs as written ("3.2" = 3 overs and 2 balls) in balls, with 5-ball overs; null when it isn't overs. */
    static Integer ballsOfOvers(JsonNode n) {
        if (n == null || !(n.isNumber() || n.isString())) return null;
        String s = n.asString("").trim();
        if (!s.matches("\\d+(\\.\\d)?")) return null;
        String[] p = s.split("\\.");
        int whole = Integer.parseInt(p[0]);
        int part = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        return whole * 5 + Math.min(part, 4);
    }

    private static Bowl bowl(JsonNode o) {
        Map<String, JsonNode> k = keys(o);
        String name = text(first(k, NAME));
        Integer balls = ballsOfOvers(first(k, OVERS));
        Integer runs = integer(first(k, RUNS)), wk = integer(first(k, WICKETS));
        if (name.isEmpty() || balls == null || runs == null || wk == null) return null;
        Integer wd = integer(first(k, WIDES)), nb = integer(first(k, NOBALLS));
        return new Bowl(name, balls, runs, wk, wd == null ? 0 : wd, nb == null ? 0 : nb);
    }

    private static Bat bat(JsonNode o) {
        Map<String, JsonNode> k = keys(o);
        String name = text(first(k, NAME));
        Integer runs = integer(first(k, RUNS)), balls = integer(first(k, BALLS));
        if (name.isEmpty() || runs == null || balls == null) return null;
        Integer fours = integer(first(k, FOURS)), sixes = integer(first(k, SIXES));
        int f = fours == null ? 0 : fours, s = sixes == null ? 0 : sixes;
        if (4 * f + 6 * s > runs) return null;
        return new Bat(name, runs, balls, f, s, text(first(k, HOW)));
    }
}

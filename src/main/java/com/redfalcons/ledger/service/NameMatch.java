package com.redfalcons.ledger.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Which squad player a typed full name is. "Ehtisham Badar" is an exact match for the squad's "Ehtisham Badar"
 * (case, extra spaces and a nickname in brackets don't matter); "Ehtisham" alone is only a possible match,
 * so the app asks "Are you Ehtisham Badar?" before linking.
 */
public final class NameMatch {

    public record Squad(String id, String name) {
    }

    /** {@code exact}: the one player the name is (or null); {@code possible}: players it may be, to ask about. */
    public record Result(Squad exact, List<Squad> possible) {
    }

    private NameMatch() {
    }

    /** "  ehtisham   BADAR (Roxy) " → "ehtisham badar". */
    public static String norm(String name) {
        if (name == null) return "";
        return name.replaceAll("\\([^)]*\\)", " ").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
    }

    public static Result match(String typed, List<Squad> squad) {
        String n = norm(typed);
        if (n.isEmpty()) return new Result(null, List.of());
        List<Squad> exact = squad.stream().filter(p -> norm(p.name()).equals(n)).toList();
        if (exact.size() == 1) return new Result(exact.get(0), List.of());
        if (exact.size() > 1) return new Result(null, exact);
        // Every typed word (3 letters or more) starts one of the player's words, in any order.
        List<String> words = Arrays.asList(n.split(" "));
        if (words.stream().anyMatch(w -> w.length() < 3)) words = words.stream().filter(w -> w.length() >= 3).toList();
        if (words.isEmpty()) return new Result(null, List.of());
        List<Squad> possible = new ArrayList<>();
        for (Squad p : squad) {
            List<String> theirs = Arrays.asList(norm(p.name()).split(" "));
            final List<String> typedWords = words;
            if (typedWords.stream().allMatch(w -> theirs.stream().anyMatch(t -> t.startsWith(w)))) possible.add(p);
        }
        return new Result(null, possible);
    }
}

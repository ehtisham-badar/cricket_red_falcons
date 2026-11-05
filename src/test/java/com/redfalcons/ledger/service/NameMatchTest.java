package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NameMatchTest {

    private static final List<NameMatch.Squad> SQUAD = List.of(
            new NameMatch.Squad("p1", "Ehtisham Badar (Roxy)"), new NameMatch.Squad("p2", "Nabeel Yousaf"),
            new NameMatch.Squad("p3", "Nabeel Sohail"), new NameMatch.Squad("p4", "Rana Abubakar Saeed"), new NameMatch.Squad("p5", "Haider"));

    @Test
    void exactNamesIgnoreCaseSpacingAndNicknames() {
        assertThat(NameMatch.match("ehtisham   badar", SQUAD).exact().id()).isEqualTo("p1");
        assertThat(NameMatch.match("HAIDER", SQUAD).exact().id()).isEqualTo("p5");
    }

    @Test
    void partNamesAreOnlyPossibleMatches() {
        NameMatch.Result r = NameMatch.match("Ehtisham", SQUAD);
        assertThat(r.exact()).isNull();
        assertThat(r.possible()).extracting(NameMatch.Squad::id).containsExactly("p1");
        assertThat(NameMatch.match("nabeel", SQUAD).possible()).extracting(NameMatch.Squad::id).containsExactly("p2", "p3");
        assertThat(NameMatch.match("Rana Abu", SQUAD).possible()).extracting(NameMatch.Squad::id).containsExactly("p4");
        // Too short to mean anything, or nobody like it.
        assertThat(NameMatch.match("Ab", SQUAD).possible()).isEmpty();
        assertThat(NameMatch.match("Imran Khan", SQUAD).possible()).isEmpty();
        assertThat(NameMatch.match("   ", SQUAD).possible()).isEmpty();
    }

    @Test
    void passwordsHashAndCheck() {
        String h = PasswordHasher.hash("secret123");
        assertThat(h).startsWith("pbkdf2$").doesNotContain("secret123");
        assertThat(PasswordHasher.matches("secret123", h)).isTrue();
        assertThat(PasswordHasher.matches("secret124", h)).isFalse();
        assertThat(PasswordHasher.hash("secret123")).isNotEqualTo(h);
    }
}

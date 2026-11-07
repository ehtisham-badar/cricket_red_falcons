package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The News post written for a finished Red Falcons match. */
class MatchReportNewsTest {

    private static Map<String, Object> match(String result) {
        return Map.of("id", 137639L, "status", "Completed", "start", "2026-11-07T12:45", "ground", "Punjab University",
                "result", result, "playerOfMatch", "Aasim  Latif",
                "teams", List.of(Map.of("name", "RED FALCONS", "runs", 103, "wickets", 10, "overs", "20.0"),
                        Map.of("name", "RAGEROVERS", "runs", 106, "wickets", 2, "overs", "13.1")));
    }

    @Test
    void aLossIsReportedWithBothScoresAndThePlayerOfTheMatch() {
        String[] post = MatchReportNews.write(match("RAGEROVERS won by 8 wickets"), 3);
        assertThat(post[0]).isEqualTo("Match 3: Red Falcons fall to Ragerovers");
        assertThat(post[1]).contains("Ragerovers won by 8 wickets").contains("Red Falcons 103 all out (20.0 ov)")
                .contains("Ragerovers 106/2 (13.1 ov)").contains("at Punjab University on 7 Nov").contains("Player of the Match: Aasim Latif.")
                .contains("come back stronger");
    }

    @Test
    void aWinIsCelebrated() {
        String[] post = MatchReportNews.write(match("RED FALCONS won by 20 runs"), 4);
        assertThat(post[0]).isEqualTo("Match 4: Red Falcons beat Ragerovers");
        assertThat(post[1]).contains("Great effort, team!");
    }
}

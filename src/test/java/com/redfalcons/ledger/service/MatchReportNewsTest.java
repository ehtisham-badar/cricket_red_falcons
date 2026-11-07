package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The News post written for a finished Red Falcons match, in the style of the team's own match reports. */
class MatchReportNewsTest {

    private static Map<String, Object> match(String result) {
        return Map.of("id", 137639L, "title", "HPL - League Stage (MATCH 8)", "status", "Completed", "start", "2026-11-07T12:45", "ground", "Punjab University",
                "result", result, "playerOfMatch", "Aasim  Latif",
                "teams", List.of(Map.of("name", "RED FALCONS", "runs", 103, "wickets", 10, "overs", "20.0"),
                        Map.of("name", "RAGEROVERS", "runs", 106, "wickets", 2, "overs", "13.1")));
    }

    private static final Map<String, Object> SUMMARY = Map.of("pointsTable", List.of(
            Map.of("team", "RAGEROVERS", "played", 3, "won", 3, "lost", 0), Map.of("team", "RED FALCONS", "played", 3, "won", 0, "lost", 3)));

    @Test
    void aLossIsReportedInTheTeamsOwnStyle() {
        String[] post = MatchReportNews.write(match("RAGEROVERS won by 8 wickets"), SUMMARY);
        assertThat(post[0]).isEqualTo("Match report: Ragerovers beat us by 8 wickets");
        assertThat(post[1]).startsWith("HPL League Stage, Match 8 · Sat 7 Nov · Punjab University\n\nResult: Red Falcons 103 all out (20 overs) · Ragerovers 106/2 (13.1 overs). Ragerovers won by 8 wickets.")
                .contains("Player of the Match: Aasim Latif.").contains("Where we stand: 2nd in the HPL table (P3 W0 L3).").contains("come back stronger");
    }

    @Test
    void aWinIsCelebrated() {
        String[] post = MatchReportNews.write(match("RED FALCONS won by 20 runs"), SUMMARY);
        assertThat(post[0]).isEqualTo("Match report: We beat Ragerovers by 20 runs");
        assertThat(post[1]).contains("Great effort, Falcons!");
    }
}

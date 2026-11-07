package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads a real Crickslab scorecard page (Red Falcons v Rage Rovers, 7 Nov 2026) into both innings. */
class TournamentScorecardsTest {

    @Test
    @SuppressWarnings("unchecked")
    void readsBothInningsFromTheScorecardPage() throws Exception {
        String html = new String(getClass().getResourceAsStream("/crickslab-scorecard-137639.html").readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> card = new TournamentScorecards(JsonMapper.builder().build(), null, null, null).parse(html);
        List<Map<String, Object>> innings = (List<Map<String, Object>>) card.get("innings");
        assertThat(innings).hasSize(2);

        Map<String, Object> first = innings.get(0);
        assertThat(first).containsEntry("team", "RED FALCONS").containsEntry("runs", 103).containsEntry("wickets", 10)
                .containsEntry("overs", "20.0").containsEntry("extras", 21).containsEntry("extrasDetail", "4b, 17wd");
        List<Map<String, Object>> bat = (List<Map<String, Object>>) first.get("batting");
        assertThat(bat).hasSize(11);
        assertThat(bat.get(0)).containsEntry("name", "Ehtisham Badar").containsEntry("how", "Hit the Wicket").containsEntry("runs", 1).containsEntry("balls", 6);
        assertThat(bat.stream().mapToInt(b -> (int) b.get("runs")).sum() + 21).isEqualTo(103);
        assertThat(bat.stream().filter(b -> !(boolean) b.get("out")).map(b -> b.get("name"))).containsExactly("Haidar");
        List<Map<String, Object>> bowl = (List<Map<String, Object>>) first.get("bowling");
        assertThat(bowl.get(0)).containsEntry("name", "JUNAID KHALID").containsEntry("overs", "4.0").containsEntry("maidens", 1).containsEntry("runs", 14).containsEntry("wickets", 1);
        assertThat((List<String>) first.get("fallOfWickets")).first().isEqualTo("1-3 (Ehtisham Badar, 1.1 Ov)");

        Map<String, Object> second = innings.get(1);
        assertThat(second).containsEntry("team", "RAGEROVERS").containsEntry("runs", 106).containsEntry("wickets", 2).containsEntry("overs", "13.1");
        assertThat((List<String>) second.get("didNotBat")).contains("Nomi");
        assertThat(((List<Map<String, Object>>) second.get("bowling")).stream().mapToInt(b -> (int) b.get("wickets")).sum()).isEqualTo(2);
    }

    @Test
    void buildsTheScorecardLinkFromTheSummaryLink() {
        assertThat(TournamentScorecards.scorecardPage("https://app.crickslab.com/matches/137639/9955f770-da55-11f1-a7ae-13ee7eccbdfa/match-between-ragerovers-and-red-falcons-scheduled-at-2026-11-07-1245-live-from-punjab-university/match-summary"))
                .hasToString("https://crickslab.com/match-central/match-details/9955f770-da55-11f1-a7ae-13ee7eccbdfa/match-between-ragerovers-and-red-falcons-scheduled-at-2026-11-07-1245-live-from-punjab-university/137639/scorecard");
        assertThat(TournamentScorecards.scorecardPage("")).isNull();
    }
}

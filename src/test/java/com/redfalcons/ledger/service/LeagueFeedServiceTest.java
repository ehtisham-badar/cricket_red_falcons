package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading Crickslab's tournament page: the page streams its data in chunks, which are joined and parsed. */
class LeagueFeedServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void readsTableResultsAndLeadersFromThePagePayload() throws Exception {
        String props = """
                {"tournamentUid":"u1","tournamentPointTableData":{"title":"Hundred Premier League","logo":"L","data":[
                  {"round_title":"League Stage","teams":[
                    {"title":"RAPTORS","logo":"r.jpg","totalMatches":1,"won":1,"lost":0,"tie":0,"draw":0,"noResult":0,"points":2,"NRR":"+1.500","form":["W"]},
                    {"title":"RED FALCONS","logo":"rf.jpg","totalMatches":0,"won":0,"lost":0,"tie":0,"draw":0,"noResult":0,"points":0,"NRR":"+0.000","form":[]}]},
                  {"round_title":"Final","teams":[]}]},
                 "tournamentAllMatchesList":{"data":[{"id":134420,"matchTitle":"HPL - League Stage (MATCH 2)","status":{"title":"Completed"},"isLive":false,
                   "scheduledDateTime":"Sat, 17 Oct 2026 07:45 AM","ground":{"title":"Punjab University"},
                   "firstTeam":{"title":"RAPTORS","logo":"r.jpg","innings":[{"totalRuns":163,"totalWickets":5,"totalOvers":"20.0"}]},
                   "secondTeam":{"title":"GALACTICOS","logo":"g.jpg","innings":[{"totalRuns":133,"totalWickets":7,"totalOvers":"20.0"}]},
                   "matchResult":{"description":"RAPTORS won by 30 runs"},"manOfTheMatch":{"name":"Abdul Mannan"},"summary_view_url":"https://x"}]},
                 "initialTopPlayerData":{"battingStanding":{"data":[{"name":"Abdul Mannan","team":{"title":"RAPTORS"},"rank":{"title":"Max Runs","value":"57"}}]},
                   "bowlingStanding":{"data":[]},"fieldingStanding":{"data":[]}}}""";
        // The page streams this as JavaScript string chunks; split it in two like the real page does.
        String escaped = JsonMapper.builder().build().writeValueAsString("0:[] 9:[\"$\",\"$L28\",null," + props + "]");
        String body = escaped.substring(1, escaped.length() - 1);
        String html = "<html><script>self.__next_f.push([1,\"" + body.substring(0, 40) + "\"])</script>"
                + "<script>self.__next_f.push([1,\"" + body.substring(40) + "\"])</script></html>";

        Map<String, Object> out = new LeagueFeedService(JsonMapper.builder().build(), "https://example").parse(html);

        assertThat(out.get("title")).isEqualTo("Hundred Premier League");
        List<Map<String, Object>> table = (List<Map<String, Object>>) out.get("pointsTable");
        assertThat(table).hasSize(2);
        assertThat(table.get(0)).containsEntry("team", "RAPTORS").containsEntry("points", 2).containsEntry("nrr", "+1.500");
        assertThat(table.get(1)).containsEntry("team", "RED FALCONS");
        Map<String, Object> match = ((List<Map<String, Object>>) out.get("results")).get(0);
        assertThat(match).containsEntry("result", "RAPTORS won by 30 runs").containsEntry("playerOfMatch", "Abdul Mannan")
                .containsEntry("ground", "Punjab University").containsEntry("start", "2026-10-17T12:45");
        Map<String, Object> first = ((List<Map<String, Object>>) match.get("teams")).get(0);
        assertThat(first).containsEntry("name", "RAPTORS").containsEntry("runs", 163).containsEntry("wickets", 5);
        Map<String, Object> leaders = (Map<String, Object>) out.get("leaders");
        assertThat(((List<Map<String, Object>>) leaders.get("batting")).get(0)).containsEntry("name", "Abdul Mannan").containsEntry("value", "57");
    }
}

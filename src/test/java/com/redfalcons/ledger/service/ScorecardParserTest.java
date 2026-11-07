package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading a scorecard page. The page below is made up in the shape the reader looks for (Crickslab's real field
 * names aren't published); it proves the reading, the choice of our lists and the checks against the totals.
 */
class ScorecardParserTest {

    private static final Set<String> OURS = Set.of("Nauman Akhtar", "Haider Sami", "Ehtisham Badar", "Talha Ramzan");

    private static String page(String ourBatting, String ourBowling, String theirBatting) {
        // Each streamed row is one line of JSON, so the text blocks below lose their line breaks first.
        String props = ("{\"match\":{\"innings\":[" +
                "{\"team\":\"RED FALCONS\",\"batting\":" + ourBatting + ",\"extras\":9}," +
                "{\"team\":\"RAGEROVERS\",\"batting\":" + theirBatting + ",\"bowling\":" + ourBowling + "}]}}").replace("\n", "");
        String escaped = JsonMapper.builder().build().writeValueAsString("0:[]\n7:" + props + "\n");
        String body = escaped.substring(1, escaped.length() - 1);
        int cut = body.indexOf(',', 30) + 1; // split between two characters that aren't part of an escape, like the real chunks
        return "<html><script>self.__next_f.push([1,\"" + body.substring(0, cut) + "\"])</script>"
                + "<script>self.__next_f.push([1,\"" + body.substring(cut) + "\"])</script></html>";
    }

    private static final String OUR_BAT = """
            [{"playerName":"Nauman Akhtar","runs":41,"balls":29,"fours":3,"sixes":1,"howOut":"c Ali b Hamza"},
             {"playerName":"Haider Sami","runs":14,"balls":12,"fours":1,"sixes":1,"howOut":"b Bilal"},
             {"playerName":"Talha Ramzan","runs":30,"balls":25,"fours":2,"sixes":0,"howOut":"not out"},
             {"playerName":"Ehtisham Badar","runs":9,"balls":10,"fours":0,"sixes":0,"howOut":"lbw b Hamza"}]""";
    private static final String THEIR_BAT = """
            [{"playerName":"Aasim Latif","runs":58,"balls":30,"fours":5,"sixes":3,"howOut":"not out"},
             {"playerName":"Abdul Ahad","runs":40,"balls":28,"fours":4,"sixes":1,"howOut":"c Nauman Akhtar b Ehtisham Badar"},
             {"playerName":"Soban","runs":0,"balls":1,"fours":0,"sixes":0,"howOut":"c & b Talha Ramzan"}]""";
    private static final String OUR_BOWL = """
            [{"playerName":"Ehtisham Badar","overs":"3.0","runs":40,"wickets":1,"wides":2,"noBalls":1},
             {"playerName":"Talha Ramzan","overs":"2.3","runs":35,"wickets":1,"wides":0,"noBalls":0},
             {"playerName":"Haider Sami","overs":"2.0","runs":28,"wickets":0,"wides":1,"noBalls":0}]""";

    @Test
    void picksOurBattingAndBowlingListsByTheSquadAndChecksTheTotals() throws Exception {
        ScorecardParser.Card card = ScorecardParser.read(JsonMapper.builder().build(), page(OUR_BAT, OUR_BOWL, THEIR_BAT),
                OURS::contains, 103, 106, 2);

        assertThat(card.batting()).extracting(ScorecardParser.Bat::name).containsExactly("Nauman Akhtar", "Haider Sami", "Talha Ramzan", "Ehtisham Badar");
        assertThat(card.batting().get(0)).satisfies(b -> {
            assertThat(b.runs()).isEqualTo(41);
            assertThat(b.balls()).isEqualTo(29);
            assertThat(b.fours()).isEqualTo(3);
            assertThat(b.sixes()).isEqualTo(1);
            assertThat(b.how()).isEqualTo("c Ali b Hamza");
        });
        // Overs are five-ball: 2.3 is 13 balls.
        assertThat(card.bowling()).extracting(ScorecardParser.Bowl::balls).containsExactly(15, 13, 10);
        assertThat(card.bowling().get(0).wides()).isEqualTo(2);
        assertThat(card.opposition()).extracting(ScorecardParser.Bat::name).contains("Aasim Latif", "Abdul Ahad");
    }

    @Test
    void aListThatDoesNotAddUpToTheTeamTotalIsNotUsed() {
        // Our runs on the card are 94 but the league page says 150: it can't be this match's card.
        assertThatThrownBy(() -> ScorecardParser.read(JsonMapper.builder().build(), page(OUR_BAT, OUR_BOWL, THEIR_BAT), OURS::contains, 150, 106, 2))
                .isInstanceOf(ScorecardParser.Unreadable.class)
                .satisfies(e -> assertThat(((ScorecardParser.Unreadable) e).sample).contains("\"runs\""));
    }

    @Test
    void aPageWithoutAScorecardFailsWithAnExplanation() {
        String html = "<html><script>self.__next_f.push([1,\"0:[]\\n\"])</script></html>";
        assertThatThrownBy(() -> ScorecardParser.read(JsonMapper.builder().build(), html, OURS::contains, 103, 106, 2))
                .isInstanceOf(ScorecardParser.Unreadable.class).hasMessageContaining("batting card was not found");
    }

    @Test
    void readsOversAndDismissals() {
        var json = JsonMapper.builder().build();
        assertThat(ScorecardParser.ballsOfOvers(json.valueToTree("3.2"))).isEqualTo(17);
        assertThat(ScorecardParser.ballsOfOvers(json.valueToTree(4))).isEqualTo(20);
        assertThat(ScorecardParser.ballsOfOvers(json.valueToTree("abc"))).isNull();

        assertThat(ScorecardImporter.dismissal("c Ali b Hamza")).containsExactly("caught", "Hamza", "Ali");
        assertThat(ScorecardImporter.dismissal("c & b Hamza")).containsExactly("caught", "Hamza", "Hamza");
        assertThat(ScorecardImporter.dismissal("b Bilal")).containsExactly("bowled", "Bilal", "");
        assertThat(ScorecardImporter.dismissal("lbw b Hamza")).containsExactly("lbw", "Hamza", "");
        assertThat(ScorecardImporter.dismissal("run out (Ali/Sami)")).containsExactly("runout", "", "Ali");
        assertThat(ScorecardImporter.dismissal("st Ali b Hamza")).containsExactly("stumped", "Hamza", "Ali");
    }

    @Test
    void onlyCrickslabPagesAreFetchedAndRobotsRulesAreFollowed() {
        assertThat(ScorecardImporter.scorecardUri("https://app.crickslab.com/matches/1/x/match-summary")).isNotNull();
        assertThat(ScorecardImporter.scorecardUri("https://crickslab.com/a")).isNotNull();
        assertThat(ScorecardImporter.scorecardUri("https://evil.example/crickslab.com")).isNull();
        assertThat(ScorecardImporter.scorecardUri("https://crickslab.com.evil.example/a")).isNull();
        assertThat(ScorecardImporter.scorecardUri("http://app.crickslab.com/a")).isNull();
        assertThat(ScorecardImporter.scorecardUri("")).isNull();

        List<String> rules = ScorecardImporter.robotsRules("""
                User-agent: BadBot
                Disallow: /

                User-agent: *
                Disallow: /api/
                Disallow: /private
                Allow: /private/open
                """);
        assertThat(ScorecardImporter.pathAllowed(rules, "/matches/1/match-summary")).isTrue();
        assertThat(ScorecardImporter.pathAllowed(rules, "/api/x")).isFalse();
        assertThat(ScorecardImporter.pathAllowed(rules, "/private/open/a")).isTrue();
        assertThat(ScorecardImporter.pathAllowed(rules, "/private/a")).isFalse();
        assertThat(ScorecardImporter.pathAllowed(ScorecardImporter.robotsRules("User-agent: *\nDisallow: /\n"), "/matches/1")).isFalse();
        assertThat(ScorecardImporter.pathAllowed(List.of(), "/anything")).isTrue();
    }

    @Test
    void readsOurCardFromTheLeaguePagesOwnMatchDataAndOutlinesTheFieldsWhenItCant() throws Exception {
        var json = JsonMapper.builder().build();
        String match = ("{\"id\":1,\"firstTeam\":{\"title\":\"RED FALCONS\",\"innings\":[{\"totalRuns\":103,\"batting\":" + OUR_BAT + "}]},"
                + "\"secondTeam\":{\"title\":\"RAGEROVERS\",\"innings\":[{\"totalRuns\":106,\"batting\":" + THEIR_BAT + ",\"bowling\":" + OUR_BOWL + "}]}}").replace("\n", "");
        ScorecardParser.Card card = ScorecardParser.read(List.of(json.readTree(match)), OURS::contains, 103, 106, 2);
        assertThat(card.batting()).hasSize(4);
        assertThat(card.bowling()).hasSize(3);

        // Nothing like a scorecard in the data: the failure lists the field names found.
        var bare = json.readTree("{\"id\":1,\"firstTeam\":{\"title\":\"X\",\"innings\":[{\"totalRuns\":103}]}}");
        assertThatThrownBy(() -> ScorecardParser.read(List.of(bare), OURS::contains, 103, 106, 2))
                .isInstanceOf(ScorecardParser.Unreadable.class)
                .satisfies(e -> assertThat(((ScorecardParser.Unreadable) e).sample).contains("fields: {id,firstTeam:{title,innings:[{totalRuns}]x1}}"));
    }
}

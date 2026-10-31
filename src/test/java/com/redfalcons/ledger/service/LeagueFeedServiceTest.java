package com.redfalcons.ledger.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading Crickslab's tournament page: the page streams its data in chunks, which are joined and parsed. */
class LeagueFeedServiceTest {

    /** A tournament page as Crickslab serves it, with one finished match. */
    private static String page() {
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
        return html;
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsTableResultsAndLeadersFromThePagePayload() throws Exception {
        String html = page();
        Map<String, Object> out = new LeagueFeedService(JsonMapper.builder().build(), "https://example", null).parse(html);

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

    @Test
    @SuppressWarnings("unchecked")
    void anOlderCachedPageNeverUndoesAFinishedMatch() {
        Map<String, Object> finished = Map.of("pointsTable", List.of(Map.of("team", "RAPTORS", "played", 1)), "leaders", Map.of("batting", List.of()),
                "results", List.of(Map.of("id", 2L, "start", "2026-10-17T12:45", "status", "Completed", "result", "RAPTORS won by 30 runs",
                        "teams", List.of(Map.of("name", "RAPTORS", "runs", 163, "wickets", 5), Map.of("name", "GALACTICOS", "runs", 133, "wickets", 7)))));
        Map<String, Object> stale = Map.of("pointsTable", List.of(Map.of("team", "RAPTORS", "played", 0)), "leaders", Map.of("batting", List.of()),
                "results", List.of(Map.of("id", 2L, "start", "2026-10-17T12:45", "status", "In-Progress", "result", "",
                        "teams", List.of(Map.of("name", "RAPTORS", "runs", 163, "wickets", 5), Map.of("name", "GALACTICOS", "runs", 129, "wickets", 6)))));

        Map<String, Object> merged = LeagueFeedService.newest(finished, stale);
        Map<String, Object> m = ((List<Map<String, Object>>) merged.get("results")).get(0);
        assertThat(m).containsEntry("status", "Completed").containsEntry("result", "RAPTORS won by 30 runs");
        assertThat(((List<Map<String, Object>>) merged.get("pointsTable")).get(0)).containsEntry("played", 1);

        // And a genuinely newer page does replace the old one.
        Map<String, Object> back = LeagueFeedService.newest(stale, finished);
        assertThat(((List<Map<String, Object>>) back.get("results")).get(0)).containsEntry("status", "Completed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aStoredCopyWithIntegerIdsDoesNotDuplicateMatches() {
        Map<String, Object> stored = Map.of("results", List.of(
                Map.of("id", 7, "start", "2026-10-31T12:45", "status", "In-Progress", "result", "", "teams", List.of(Map.of("name", "RAPTORS", "runs", 163, "wickets", 8))),
                Map.of("id", 7, "start", "2026-10-31T12:45", "status", "In-Progress", "result", "", "teams", List.of(Map.of("name", "RAPTORS", "runs", 100, "wickets", 2))),
                Map.of("id", 3, "start", "2026-10-24T09:30", "status", "Completed", "result", "ANGGLER won", "teams", List.of())));
        Map<String, Object> fresh = Map.of("results", List.of(
                Map.of("id", 7L, "start", "2026-10-31T12:45", "status", "Completed", "result", "RAPTORS won by 43 runs", "teams", List.of(Map.of("name", "RAPTORS", "runs", 163, "wickets", 8))),
                Map.of("id", 3L, "start", "2026-10-24T09:30", "status", "Completed", "result", "ANGGLER won", "teams", List.of())));
        List<Map<String, Object>> results = (List<Map<String, Object>>) LeagueFeedService.newest(stored, fresh).get("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsEntry("result", "RAPTORS won by 43 runs");
    }

    @Test
    void keepsAskingOnSaturdayWhileThatDaysMatchIsUnfinished() {
        Map<String, Object> copy = Map.of("results", List.of(
                Map.of("id", 7L, "start", "2026-10-31T12:45", "status", "In-Progress"),
                Map.of("id", 5L, "start", "2026-10-24T12:45", "status", "TossDone")));
        assertThat(LeagueFeedService.unfinishedOn(copy, java.time.LocalDate.parse("2026-10-31"))).isTrue();
        // An old match stuck on Crickslab doesn't keep us asking on a later day.
        assertThat(LeagueFeedService.unfinishedOn(copy, java.time.LocalDate.parse("2026-11-07"))).isFalse();
        assertThat(LeagueFeedService.unfinishedOn(null, java.time.LocalDate.parse("2026-10-31"))).isFalse();
    }

    @Test
    void fetchesFromSaturdayFourPmPakistanTime() {
        // Sat 31 Oct 2026, 4 pm in Pakistan is 11:00 UTC.
        java.time.Instant sat4pm = java.time.Instant.parse("2026-10-31T11:00:00Z");
        assertThat(LeagueFeedService.lastFetchTime(sat4pm)).isEqualTo(sat4pm);
        assertThat(LeagueFeedService.lastFetchTime(sat4pm.plusSeconds(3600 * 30))).isEqualTo(sat4pm);       // Sunday evening
        assertThat(LeagueFeedService.lastFetchTime(sat4pm.minusSeconds(60))).isEqualTo(sat4pm.minusSeconds(7 * 86400)); // Saturday 3:59 pm
    }

    @Test
    void aFetchedCopyIsStoredAndServedAfterARestartWithoutFetching() throws Exception {
        com.sun.net.httpserver.HttpServer site = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        site.createContext("/", ex -> {
            hits.incrementAndGet();
            byte[] b = page().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        site.start();
        String url = "http://127.0.0.1:" + site.getAddress().getPort() + "/";
        Map<String, com.redfalcons.ledger.model.LeagueSnapshot> rows = new java.util.HashMap<>();
        com.redfalcons.ledger.repo.LeagueSnapshotRepository store = org.mockito.Mockito.mock(com.redfalcons.ledger.repo.LeagueSnapshotRepository.class);
        org.mockito.Mockito.when(store.save(org.mockito.ArgumentMatchers.any())).thenAnswer(a -> {
            com.redfalcons.ledger.model.LeagueSnapshot r = a.getArgument(0);
            rows.put(r.getId(), r);
            return r;
        });
        org.mockito.Mockito.when(store.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(a -> java.util.Optional.ofNullable(rows.get(a.getArgument(0))));
        try {
            LeagueFeedService first = new LeagueFeedService(JsonMapper.builder().build(), url, store);
            // Opening the app never fetches: nothing is stored yet, so there is nothing to show.
            assertThat(first.summary()).containsEntry("available", false);
            assertThat(hits.get()).isZero();
            // The scheduled check finds no copy at all, so it fetches once and stores it.
            first.scheduledRefresh();
            assertThat(hits.get()).isEqualTo(1);
            assertThat(first.summary()).containsEntry("available", true);
            // Already fetched since the last Saturday 4 pm: the next checks don't fetch again.
            first.scheduledRefresh();
            assertThat(hits.get()).isEqualTo(1);
        } finally {
            site.stop(0);
        }
        // After a restart the stored copy is served straight from the database.
        LeagueFeedService restarted = new LeagueFeedService(JsonMapper.builder().build(), url, store);
        assertThat(restarted.summary()).containsEntry("available", true).containsEntry("title", "Hundred Premier League");
        assertThat(hits.get()).isEqualTo(1);
    }
}

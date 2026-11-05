package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:engagetest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class EngageTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String admin, String viewer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (admin != null) b.header("X-Admin-Token", admin);
        if (viewer != null) b.header("X-Viewer", viewer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]*)\".*", "$1");
    }

    private static String ledger(String m1Result, boolean m1Finished) {
        String future = LocalDate.now().plusDays(10).toString();
        return "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{\"m1\":true}},"
                + "{\"id\":\"p3\",\"name\":\"Haris\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}],"
                + "\"matches\":[{\"id\":\"m1\",\"date\":\"2030-05-04\",\"opponent\":\"Raptors\",\"venue\":\"Park\",\"finished\":" + m1Finished
                + ",\"result\":\"" + m1Result + "\"},{\"id\":\"m2\",\"date\":\"" + future + "\",\"opponent\":\"Lions\",\"venue\":\"Ground\",\"finished\":false}]}";
    }

    @Test
    void momPollsPredictionsAndNotes() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", ledger("", false), admin, null).statusCode()).isEqualTo(200);

        // Predictions: a player guesses before the match; counts stay hidden until it starts.
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"win\"}", null, null).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"draw\"}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"win\"}", null, "p1").statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"win\"}", null, "p2").statusCode()).isEqualTo(200);
        // A prediction is locked once made: it can't be changed or repeated.
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"loss\"}", null, "p2").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"win\"}", null, "p1").statusCode()).isEqualTo(400);
        // Someone who isn't a squad member (no valid player) can't predict.
        assertThat(send("PUT", "/api/engage/predict/m2", "{\"pick\":\"win\"}", null, "test").statusCode()).isEqualTo(400);
        String viewerView = send("GET", "/api/engage", null, null, "p1").body();
        assertThat(viewerView).contains("\"mine\":\"win\"").contains("\"locked\":false").doesNotContain("\"counts\":{\"win\"");
        assertThat(send("GET", "/api/engage", null, admin, null).body()).contains("\"counts\":{\"win\":2}")
                .contains("\"picks\":[{\"playerId\":\"p1\",\"pick\":\"win\"}");
        // Only the admin learns who guessed what.
        assertThat(viewerView).contains("\"picks\":[]");
        // Predictions on a finished match can't be changed.
        assertThat(send("PUT", "/api/engage/predict/m1", "{\"pick\":\"win\"}", null, "p1").statusCode()).isEqualTo(200);

        // Finish m1 as a win: p1 guessed it (picked before), the leaderboard counts it.
        assertThat(send("PUT", "/api/ledger", ledger("win", true), admin, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/predict/m1", "{\"pick\":\"loss\"}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/engage", null, null, null).body()).contains("\"leaderboard\":[{\"playerId\":\"p1\",\"points\":1,\"correct\":1,\"played\":1}]");
        assertThat(send("GET", "/api/ledger", null, null, null).body()).contains("\"result\":\"win\"");
        // The admin still sees who guessed a finished match, with its result; players don't get that row.
        assertThat(send("GET", "/api/engage", null, admin, null).body()).contains("\"finished\":true,\"result\":\"win\"");
        assertThat(send("GET", "/api/engage", null, null, "p1").body()).doesNotContain("\"finished\":true");

        // Man of the match: only players who played, not yourself, only once finished; closing announces it.
        assertThat(send("PUT", "/api/engage/mom/m2", "{\"candidateId\":\"p2\"}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/mom/m1", "{\"candidateId\":\"p3\"}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/mom/m1", "{\"candidateId\":\"p1\"}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/mom/m1", "{\"candidateId\":\"p2\"}", null, null).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/engage/mom/m1", "{\"candidateId\":\"p2\"}", null, "p1").statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/engage", null, null, "p3").body()).doesNotContain("\"counts\":{\"p2\"");
        assertThat(send("GET", "/api/engage", null, null, "p1").body()).contains("\"mine\":\"p2\"").contains("\"counts\":{\"p2\":1}");
        assertThat(send("POST", "/api/engage/mom/m1/close", null, null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/engage/mom/m1/close", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/mom/m1", "{\"candidateId\":\"p1\"}", null, "p2").statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/engage", null, null, "p3").body()).contains("\"winners\":[\"p2\"]");
        assertThat(send("GET", "/api/notifications", null, null, null).body()).contains("Rizwan — man of the match vs Raptors");

        // Polls: staff ask, anyone answers once (changeable), results stay hidden until you answer.
        assertThat(send("POST", "/api/engage/polls", "{\"question\":\"Nets on Friday?\",\"options\":[\"Yes\",\"No\"]}", null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/engage/polls", "{\"question\":\"Nets?\",\"options\":[\"Yes\"]}", admin, null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/engage/polls", "{\"question\":\"Nets on Friday?\",\"options\":[\"Yes\",\"No\",\"Maybe\"]}", admin, null).statusCode()).isEqualTo(200);
        String pollId = field(send("GET", "/api/engage", null, null, "p1").body().replaceAll(".*\"polls\":\\[\\{\"id\":\"([^\"]*)\".*", "{\"id\":\"$1\"}"), "id");
        assertThat(send("PUT", "/api/engage/polls/" + pollId + "/vote", "{\"option\":9}", null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/engage", null, null, "p1").body()).contains("\"mine\":null").doesNotContain("\"count\":1");
        assertThat(send("PUT", "/api/engage/polls/" + pollId + "/vote", "{\"option\":0}", null, "p1").statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/polls/" + pollId + "/vote", "{\"option\":1}", null, "p1").statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/polls/" + pollId + "/vote", "{\"option\":2}", null, "p2").statusCode()).isEqualTo(200);
        String seen = send("GET", "/api/engage", null, null, "p1").body();
        assertThat(seen).contains("\"mine\":1").contains("{\"text\":\"No\",\"count\":1}").contains("\"voters\":[]");
        assertThat(send("GET", "/api/engage", null, admin, null).body()).contains("{\"name\":\"Babar\",\"option\":\"No\"}");
        assertThat(send("POST", "/api/engage/polls/" + pollId + "/toggle", null, null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/engage/polls/" + pollId + "/toggle", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/engage/polls/" + pollId + "/vote", "{\"option\":0}", null, "p3").statusCode()).isEqualTo(400);
        assertThat(send("DELETE", "/api/engage/polls/" + pollId, null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/engage", null, admin, null).body()).contains("\"polls\":[]");

        // The captain's notes are for staff only.
        assertThat(send("GET", "/api/engage/notes/m2", null, null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/engage/notes/m2", "{\"fieldPlan\":\"x\"}", null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/engage/notes/m2", "{\"fieldPlan\":\"Two slips\",\"battingPlan\":\"Babar opens\",\"opposition\":\"Strong spinners\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/engage/notes/m2", null, admin, null).body()).contains("Two slips").contains("Babar opens").contains("Strong spinners").contains("\"updatedBy\":\"Admin\"");
        // Everything about a deleted match goes with it.
        send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}", admin, null);
        assertThat(send("GET", "/api/engage/notes/m2", null, admin, null).body()).contains("\"fieldPlan\":\"\"");
    }
}

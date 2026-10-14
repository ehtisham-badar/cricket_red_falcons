package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:managertest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class ManagerAccessTest {

    private static final String STATE = """
            {"players":[{"id":"p1","name":"Babar","att":{}},{"id":"p2","name":"Rizwan","att":{}}],
             "trainings":[{"id":"t1","desc":""}],
             "matches":[{"id":"m1","finished":false},{"id":"m2","finished":true}],
             "settings":{"currency":"Rs ","maxPerMatch":1}}
            """;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String adminToken, String managerToken) throws Exception {
        return send(method, path, body, adminToken, managerToken, null);
    }

    private HttpResponse<String> send(String method, String path, String body, String adminToken, String managerToken, String requestToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (adminToken != null) b.header("X-Admin-Token", adminToken);
        if (managerToken != null) b.header("X-Manager-Token", managerToken);
        if (requestToken != null) b.header("X-Request-Token", requestToken);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void managerSignsInWithTheirPhraseAndCanSetOnlyTheXiUntilRevoked() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", STATE, admin, null).statusCode()).isEqualTo(200);

        // A phrase must be long enough, and can't be the admin key.
        assertThat(send("POST", "/api/manager/request", "{\"name\":\"Ali\",\"phrase\":\"short\"}", null, null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/manager/request", "{\"name\":\"Ali\",\"phrase\":\"test-key\"}", null, null).statusCode()).isEqualTo(400);
        String ask = send("POST", "/api/manager/request", "{\"name\":\"Ali\",\"phrase\":\"blue falcon rises\",\"label\":\"Phone\"}", null, null).body();
        String secret = field(ask, "token");
        assertThat(send("POST", "/api/manager/request", "{\"name\":\"Sam\",\"phrase\":\"Blue Falcon Rises\"}", null, null).statusCode()).isEqualTo(400);

        // Pending: the phrase signs in as an ordinary viewer, and the admin sees the phrase.
        assertThat(send("GET", "/api/manager/status", null, null, null, secret).body()).contains("\"pending\"");
        assertThat(send("POST", "/api/login", "{\"name\":\"blue falcon rises\"}", null, null).body()).doesNotContain("managerToken");
        String listed = send("GET", "/api/manager/inbox", null, admin, null).body();
        assertThat(listed).contains("\"phrase\":\"blue falcon rises\"");
        String id = field(listed, "id");
        assertThat(send("GET", "/api/manager/inbox", null, null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/manager/requests/" + id + "/approve", null, null, null).statusCode()).isEqualTo(403);

        // Approved: the phrase signs in straight away (no two-factor step) as a manager, not the admin.
        assertThat(send("POST", "/api/manager/requests/" + id + "/approve", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/status", null, null, null, secret).body()).contains("\"approved\"");
        String login = send("POST", "/api/login", "{\"name\":\"Blue Falcon Rises\"}", null, null).body();
        assertThat(login).contains("\"manager\":true").contains("\"admin\":false").contains("\"token\":null").contains("\"name\":\"Ali\"");
        String mgr = field(login, "managerToken");
        assertThat(send("GET", "/api/manager/session", null, null, mgr).body()).contains("true");

        // A manager only proposes a lineup: nothing changes until the admin approves, and the normal save is closed.
        String xi = "{\"matchId\":\"m1\",\"playing\":[\"p1\"],\"twelfth\":\"p2\"}";
        assertThat(send("POST", "/api/manager/lineups", xi, null, null).statusCode()).isEqualTo(403);
        String sent = send("POST", "/api/manager/lineups", xi, null, mgr).body();
        assertThat(sent).contains("\"status\":\"pending\"");
        String lineupId = field(sent, "id");
        assertThat(send("PUT", "/api/ledger", STATE, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/ledger", null, null, null).body()).doesNotContain("\"m1\":true").contains("\"twelfth\":{}");
        assertThat(send("GET", "/api/manager/inbox", null, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/manager/inbox", null, admin, null).body()).contains("\"lineups\":[{\"id\":\"" + lineupId);

        // Too many players, and finished matches, are refused up front.
        assertThat(send("POST", "/api/manager/lineups", "{\"matchId\":\"m1\",\"playing\":[\"p1\",\"p2\"]}", null, mgr).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/manager/lineups", "{\"matchId\":\"m2\",\"playing\":[\"p1\"]}", null, mgr).statusCode()).isEqualTo(400);

        // Declining carries a note back to the manager; the ledger stays as it was.
        assertThat(send("POST", "/api/manager/lineups/" + lineupId + "/approve", null, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/manager/lineups/" + lineupId + "/decline", "{\"note\":\"Babar is injured\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/lineups", null, null, mgr).body()).contains("\"status\":\"declined\"").contains("Babar is injured");
        assertThat(send("GET", "/api/ledger", null, null, null).body()).doesNotContain("\"m1\":true");

        // Approving applies it to the ledger.
        String second = send("POST", "/api/manager/lineups", xi, null, mgr).body();
        assertThat(send("POST", "/api/manager/lineups/" + field(second, "id") + "/approve", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null, null).body()).contains("\"att\":{\"m1\":true}").contains("\"twelfth\":{\"m1\":\"p2\"}");

        // Changing the phrase signs the manager out; only the new phrase works.
        assertThat(send("PUT", "/api/manager/requests/" + id + "/phrase", "{\"phrase\":\"short\"}", admin, null).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/manager/requests/" + id + "/phrase", "{\"phrase\":\"green falcon sets\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/manager/lineups", xi, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/login", "{\"name\":\"blue falcon rises\"}", null, null).body()).doesNotContain("managerToken");
        String again = send("POST", "/api/login", "{\"name\":\"green falcon sets\"}", null, null).body();
        assertThat(again).contains("\"manager\":true");
        String mgr2 = field(again, "managerToken");

        // Revoked: nothing works any more.
        assertThat(send("DELETE", "/api/manager/requests/" + id, null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/session", null, null, mgr2).body()).contains("false");
        assertThat(send("POST", "/api/manager/lineups", xi, null, mgr2).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/manager/status", null, null, null, secret).body()).contains("\"none\"");
        assertThat(send("POST", "/api/login", "{\"name\":\"green falcon sets\"}", null, null).body()).doesNotContain("managerToken");

        // A declined access request keeps the admin's note, and the phrase is free again.
        String ask2 = send("POST", "/api/manager/request", "{\"name\":\"Sam\",\"phrase\":\"green falcon sets\"}", null, null).body();
        String id2 = field(send("GET", "/api/manager/inbox", null, admin, null).body(), "id");
        assertThat(send("POST", "/api/manager/requests/" + id2 + "/decline", "{\"note\":\"Not this season\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/status", null, null, null, field(ask2, "token")).body()).contains("\"declined\"").contains("Not this season");
        assertThat(send("POST", "/api/login", "{\"name\":\"green falcon sets\"}", null, null).body()).doesNotContain("managerToken");
    }
}

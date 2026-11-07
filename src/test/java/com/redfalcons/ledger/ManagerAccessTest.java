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
    void managerIsAnAccountWithTheManagerRoleAndCanSetOnlyTheXiUntilTheRoleIsTakenAway() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", STATE, admin, null).statusCode()).isEqualTo(200);

        // Admin devices get a push for each lineup proposal; other devices don't.
        String adminDevice = "https://push.example/admin1", viewerDevice = "https://push.example/viewer1";
        assertThat(send("POST", "/api/push/subscribe", "{\"endpoint\":\"" + adminDevice + "\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/push/subscribe", "{\"endpoint\":\"" + viewerDevice + "\"}", null, null).statusCode()).isEqualTo(200);
        String pendingUrl = "/api/push/pending?endpoint=";

        // The old phrase sign-in and the request flow are gone.
        assertThat(send("POST", "/api/manager/request", "{\"name\":\"Ali\",\"phrase\":\"blue falcon rises\"}", null, null).statusCode()).isIn(404, 405);
        assertThat(send("POST", "/api/login", "{\"name\":\"blue falcon rises\"}", null, null).body()).doesNotContain("managerToken");

        // Only the admin can give a role, and only manager, admin or none.
        StaffLogins.Staff ali = StaffLogins.create(port, admin, "Ali", null);
        String roleUrl = "/api/account/users/" + ali.id() + "/role";
        assertThat(send("PUT", roleUrl, "{\"role\":\"manager\"}", null, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", roleUrl, "{\"role\":\"boss\"}", admin, null).statusCode()).isEqualTo(400);
        assertThat(send("PUT", roleUrl, "{\"role\":\"manager\"}", admin, null).body()).contains("\"role\":\"manager\"");
        assertThat(send("GET", "/api/account/users", null, admin, null).body()).contains("\"role\":\"manager\"");

        // With the role, signing in gives a manager session (not an admin one).
        String login = send("POST", "/api/account/login", "{\"email\":\"" + ali.email() + "\",\"password\":\"secret123\"}", null, null).body();
        assertThat(login).contains("\"managerToken\":\"").doesNotContain("adminToken");
        String mgr = field(login, "managerToken");
        assertThat(send("GET", "/api/manager/session", null, null, mgr).body()).contains("true").contains(ali.id());

        // A manager only proposes a lineup: nothing changes until the admin approves, and the normal save is closed.
        String xi = "{\"matchId\":\"m1\",\"playing\":[\"p1\"],\"twelfth\":\"p2\"}";
        assertThat(send("POST", "/api/manager/lineups", xi, null, null).statusCode()).isEqualTo(403);
        String sent = send("POST", "/api/manager/lineups", xi, null, mgr).body();
        assertThat(sent).contains("\"status\":\"pending\"");
        assertThat(send("GET", pendingUrl + adminDevice, null, null, null).body()).contains("Lineup proposal").contains("Ali proposed the XI for Match 1 (1 playing)");
        assertThat(send("GET", pendingUrl + viewerDevice, null, null, null).body()).isEqualTo("[]");
        String lineupId = field(sent, "id");
        assertThat(send("PUT", "/api/ledger", STATE, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).doesNotContain("\"m1\":true").contains("\"twelfth\":{}");
        assertThat(send("GET", "/api/manager/inbox", null, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/manager/inbox", null, admin, null).body()).contains("\"lineups\":[{\"id\":\"" + lineupId);

        // Too many players, and finished matches, are refused up front.
        assertThat(send("POST", "/api/manager/lineups", "{\"matchId\":\"m1\",\"playing\":[\"p1\",\"p2\"]}", null, mgr).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/manager/lineups", "{\"matchId\":\"m2\",\"playing\":[\"p1\"]}", null, mgr).statusCode()).isEqualTo(400);

        // Declining carries a note back to the manager; the ledger stays as it was.
        assertThat(send("POST", "/api/manager/lineups/" + lineupId + "/approve", null, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/manager/lineups/" + lineupId + "/decline", "{\"note\":\"Babar is injured\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/lineups", null, null, mgr).body()).contains("\"status\":\"declined\"").contains("Babar is injured");
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).doesNotContain("\"m1\":true");

        // Approving applies it to the ledger, batting order included (players outside the XI are dropped).
        String withOrder = "{\"matchId\":\"m1\",\"playing\":[\"p1\",\"p2\"],\"batting\":[\"p2\",\"ghost\",\"p1\"]}";
        assertThat(send("PUT", "/api/ledger", STATE.replace("\"maxPerMatch\":1", "\"maxPerMatch\":11"), admin, null).statusCode()).isEqualTo(200);
        String third = send("POST", "/api/manager/lineups", withOrder, null, mgr).body();
        assertThat(third).contains("\"batting\":[\"p2\",\"p1\"]");
        assertThat(send("POST", "/api/manager/lineups/" + field(third, "id") + "/approve", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).contains("\"battingOrder\":{\"m1\":[\"p2\",\"p1\"]}");
        assertThat(send("PUT", "/api/ledger", STATE, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).contains("\"battingOrder\":{}");

        String second = send("POST", "/api/manager/lineups", xi, null, mgr).body();
        assertThat(send("POST", "/api/manager/lineups/" + field(second, "id") + "/approve", null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).contains("\"att\":{\"m1\":true}").contains("\"twelfth\":{\"m1\":\"p2\"}");

        // Taking the role away signs the manager out at once; they can't sign in as one again.
        assertThat(send("PUT", roleUrl, "{\"role\":\"\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/manager/session", null, null, mgr).body()).contains("false");
        assertThat(send("POST", "/api/manager/lineups", xi, null, mgr).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/account/login", "{\"email\":\"" + ali.email() + "\",\"password\":\"secret123\"}", null, null).body())
                .doesNotContain("managerToken").doesNotContain("adminToken");
    }

    @Test
    void anAccountWithTheAdminRoleSignsInAsAdminUntilTheRoleIsTakenAway() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        StaffLogins.Staff boss = StaffLogins.create(port, admin, "Boss", "admin");
        assertThat(boss.token()).isNotEmpty();

        // The account's admin session works like the admin key's: it can read and save the ledger, and give roles.
        assertThat(send("PUT", "/api/ledger", STATE, boss.token(), null).statusCode()).isEqualTo(200);
        StaffLogins.Staff helper = StaffLogins.create(port, boss.token(), "Helper", "manager");
        assertThat(helper.token()).isNotEmpty();

        // Without the role there is no admin session, and a plain account can't give roles.
        StaffLogins.Staff plain = StaffLogins.create(port, admin, "Plain", null);
        assertThat(send("PUT", "/api/account/users/" + plain.id() + "/role", "{\"role\":\"admin\"}", null, null).statusCode()).isEqualTo(403);

        // Taking the role away ends its admin session straight away.
        assertThat(send("PUT", "/api/account/users/" + boss.id() + "/role", "{\"role\":\"\"}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/ledger", STATE, boss.token(), null).statusCode()).isEqualTo(403);
        assertThat(StaffLogins.signIn(port, boss.email(), "admin")).isEmpty();
    }
}

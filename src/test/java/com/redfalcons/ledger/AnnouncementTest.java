package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:announcetest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class AnnouncementTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String admin, String manager) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (admin != null) b.header("X-Admin-Token", admin);
        if (manager != null) b.header("X-Manager-Token", manager);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]*)\".*", "$1");
    }

    @Test
    void staffAnnounceInAppAndEveryoneReadsThem() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        // Set up an approved manager.
        send("POST", "/api/manager/request", "{\"name\":\"Ali\",\"phrase\":\"blue falcon rises\"}", null, null);
        String mid = field(send("GET", "/api/manager/inbox", null, admin, null).body(), "id");
        send("POST", "/api/manager/requests/" + mid + "/approve", null, admin, null);
        String mgr = field(send("POST", "/api/login", "{\"name\":\"blue falcon rises\"}", null, null).body(), "managerToken");

        // Viewers can neither write nor list announcements.
        String msg = "{\"title\":\"Match reminder\",\"body\":\"Match 3 vs Lions on Saturday. Report at 2:30 pm.\",\"matchId\":\"m3\"}";
        assertThat(send("POST", "/api/announcements", msg, null, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/announcements", null, null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/announcements", "{\"body\":\"x\"}", "forged", null).statusCode()).isEqualTo(403);

        // The admin's message shows up in everyone's notifications, with who wrote it.
        assertThat(send("POST", "/api/announcements", "{\"title\":\"x\",\"body\":\"  \"}", admin, null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/announcements", msg, admin, null).statusCode()).isEqualTo(200);
        String feed = send("GET", "/api/notifications", null, null, null).body();
        assertThat(feed).contains("Match reminder").contains("Report at 2:30 pm").contains("\"author\":\"Admin\"")
                .contains("\"kind\":\"announcement\"").contains("\"matchId\":\"m3\"");

        // A manager can write one too, and can delete only their own.
        HttpResponse<String> byManager = send("POST", "/api/announcements", "{\"title\":\"Nets\",\"body\":\"Nets at 5 pm\"}", null, mgr);
        assertThat(byManager.statusCode()).isEqualTo(200);
        assertThat(byManager.body()).contains("\"author\":\"Ali\"");
        String list = send("GET", "/api/announcements", null, null, mgr).body();
        String adminsId = field(list.substring(list.indexOf("Match reminder") - 60), "id");
        assertThat(send("DELETE", "/api/announcements/" + adminsId, null, null, mgr).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/announcements/" + field(byManager.body(), "id"), null, null, mgr).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications", null, null, null).body()).doesNotContain("Nets at 5 pm");

        // A later one is held back: staff see it as scheduled, everyone else only once its time comes.
        String later = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        HttpResponse<String> scheduled = send("POST", "/api/announcements",
                "{\"title\":\"Match tomorrow\",\"body\":\"Be ready\",\"sendAt\":\"" + later + "\"}", admin, null);
        assertThat(scheduled.statusCode()).isEqualTo(200);
        assertThat(scheduled.body()).contains("\"status\":\"scheduled\"").contains(later);
        assertThat(send("GET", "/api/notifications", null, null, null).body()).doesNotContain("Match tomorrow");
        assertThat(send("GET", "/api/announcements", null, admin, null).body()).contains("Match tomorrow");
        assertThat(send("POST", "/api/announcements", "{\"body\":\"x\",\"sendAt\":\"tomorrow\"}", admin, null).statusCode()).isEqualTo(400);

        // A time already past just sends now; the admin can delete anything.
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        assertThat(send("POST", "/api/announcements", "{\"title\":\"Late\",\"body\":\"Sent now\",\"sendAt\":\"" + past + "\"}", admin, null).body())
                .contains("\"status\":\"sent\"");
        assertThat(send("DELETE", "/api/announcements/" + field(scheduled.body(), "id"), null, admin, null).statusCode()).isEqualTo(200);

        // Revoking the manager takes their writing rights away.
        send("DELETE", "/api/manager/requests/" + mid, null, admin, null);
        assertThat(send("POST", "/api/announcements", msg, null, mgr).statusCode()).isEqualTo(403);
    }
}

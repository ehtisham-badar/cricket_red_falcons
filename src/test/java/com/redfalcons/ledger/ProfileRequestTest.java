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
        "spring.datasource.url=jdbc:h2:mem:profilereqtest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class ProfileRequestTest {

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

    @Test
    void profileRequestAnnouncementAndAdminNotice() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        String ledger = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}],"
                + "\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}],\"matches\":[]}";
        assertThat(send("PUT", "/api/ledger", ledger, admin, null).statusCode()).isEqualTo(200);

        // The announcement carries the "update your profile" action into everyone's feed.
        assertThat(send("POST", "/api/announcements", "{\"title\":\"Update your profile\",\"body\":\"Add your photo and jersey\",\"action\":\"profile\"}", admin, null)
                .body()).contains("\"action\":\"profile\"");
        assertThat(send("POST", "/api/announcements", "{\"title\":\"Hello\",\"body\":\"Plain\",\"action\":\"hack\"}", admin, null).body())
                .contains("\"action\":\"\"");
        String feed = send("GET", "/api/notifications", null, null, null).body();
        assertThat(feed).contains("\"action\":\"profile\"");

        // Nothing for the admin yet; viewers can't read the admin notices at all.
        assertThat(send("GET", "/api/notifications/admin", null, null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/notifications/admin", null, admin, null).body()).isEqualTo("[]");

        // A player saves their profile (jersey name and number included): the admin hears about it, nobody else does.
        String mine = "{\"battingHand\":\"right\",\"bowlingArm\":\"left\",\"bowlingStyle\":\"Leg spin\",\"jersey\":\"56x\",\"jerseyName\":\"BABAR\",\"submit\":true}";
        assertThat(send("PUT", "/api/players/p1/profile", mine, null, "p2").statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/players/p1/profile", mine, null, "p1").statusCode()).isEqualTo(200);
        String updates = send("GET", "/api/notifications/admin", null, admin, null).body();
        assertThat(updates).contains("Profile updated").contains("Babar updated their profile").contains("BABAR #56").contains("right-hand bat");
        assertThat(send("GET", "/api/notifications", null, null, null).body()).doesNotContain("Babar updated");

        String view = send("GET", "/api/ledger", null, null, "p1").body();
        assertThat(view).contains("\"jersey\":\"56\"").contains("\"jerseyName\":\"BABAR\"");
        assertThat(view.substring(view.indexOf("\"id\":\"p1\""), view.indexOf("\"id\":\"p2\""))).containsPattern("\"profileAt\":\"20");

        // Saving without "submit" (or by the admin) stays quiet, and a full admin save keeps the name and the saved time.
        send("PUT", "/api/players/p1/profile", "{\"battingHand\":\"left\"}", null, "p1");
        send("PUT", "/api/players/p1/profile", "{\"submit\":true}", admin, null);
        assertThat(send("GET", "/api/notifications/admin", null, admin, null).body().split("Profile updated", -1).length - 1).isEqualTo(1);
        assertThat(send("PUT", "/api/ledger", ledger, admin, null).statusCode()).isEqualTo(200);
        String after = send("GET", "/api/ledger", null, null, null).body();
        assertThat(after).contains("\"jerseyName\":\"BABAR\"").contains("\"jersey\":\"56\"");
    }
}

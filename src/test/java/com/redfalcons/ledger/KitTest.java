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
        "spring.datasource.url=jdbc:h2:mem:kittest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class KitTest {

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

    @Test
    void shirtSizeRequestIsAnnouncedAndAnswered() throws Exception {
        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body().replaceAll(".*\"token\":\"([^\"]*)\".*", "$1");
        String ledger = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}";
        assertThat(send("PUT", "/api/ledger", ledger, admin, null).statusCode()).isEqualTo(200);

        // The request goes into everyone's feed with the "kit" action (anything else is dropped).
        assertThat(send("POST", "/api/announcements", "{\"title\":\"Choose your shirt size\",\"body\":\"Pick a size\",\"action\":\"kit\"}", admin, null).body())
                .contains("\"action\":\"kit\"");
        assertThat(send("GET", "/api/notifications", null, null, null).body()).contains("\"action\":\"kit\"");

        // A player answers: only a size from the chart is accepted; the admin hears about it.
        String bad = "{\"shirtSize\":\"XXXL\",\"jersey\":\"7\",\"jerseyName\":\"BABAR\",\"kit\":true}";
        assertThat(send("PUT", "/api/players/p1/profile", bad, null, "p1").statusCode()).isEqualTo(400);
        String good = "{\"shirtSize\":\"2xl\",\"jersey\":\"7\",\"jerseyName\":\"BABAR\",\"kit\":true}";
        assertThat(send("PUT", "/api/players/p2/profile", good, null, "p1").statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/players/p1/profile", good, null, "p1").statusCode()).isEqualTo(200);
        String updates = send("GET", "/api/notifications/admin", null, admin, null).body();
        assertThat(updates).contains("Kit details").contains("Babar chose size 2XL").contains("BABAR #7");
        assertThat(send("GET", "/api/notifications", null, null, null).body()).doesNotContain("Babar chose");

        // Sizes are for the admin; a player sees only their own.
        assertThat(send("GET", "/api/ledger", null, admin, null).body()).contains("\"shirtSize\":\"2XL\"").containsPattern("\"kitAt\":\"20");
        assertThat(send("GET", "/api/ledger", null, null, "p1").body()).contains("\"shirtSize\":\"2XL\"");
        assertThat(send("GET", "/api/ledger", null, null, "p2").body()).doesNotContain("\"shirtSize\":\"2XL\"");
        assertThat(send("GET", "/api/ledger", null, null, null).body()).doesNotContain("\"shirtSize\":\"2XL\"");

        // The admin can set a size for someone, and a full save keeps everyone's.
        String set = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{},\"shirtSize\":\"M\"}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}";
        assertThat(send("PUT", "/api/ledger", set, admin, null).statusCode()).isEqualTo(200);
        String after = send("GET", "/api/ledger", null, admin, null).body();
        assertThat(after).contains("\"shirtSize\":\"2XL\"").contains("\"shirtSize\":\"M\"");
    }
}

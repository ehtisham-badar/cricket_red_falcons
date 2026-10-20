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
        "spring.datasource.url=jdbc:h2:mem:feedtest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class NotificationFeedTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Admin-Token", token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String ledger(String expenses) {
        return "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}],"
                + "\"matches\":[],\"expenses\":{\"t1\":[" + expenses + "]},\"settings\":{\"currency\":\"Rs \",\"maxPerMatch\":11}}";
    }

    private static String exp(String id, String item, String amount) {
        return "{\"id\":\"" + id + "\",\"item\":\"" + item + "\",\"amount\":\"" + amount + "\",\"paidBy\":\"p1\"}";
    }

    @Test
    void viewersSeeNewExpensesOnceAndEditsUpdateThem() throws Exception {
        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body()
                .replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        assertThat(send("GET", "/api/notifications", null, null).body()).isEqualTo("[]");

        // A blank row says nothing yet; once it has an amount, everyone hears about it.
        assertThat(send("PUT", "/api/ledger", ledger(exp("e1", "", "")), admin).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications", null, null).body()).isEqualTo("[]");
        send("PUT", "/api/ledger", ledger(exp("e1", "Water", "5")), admin);
        String feed = send("GET", "/api/notifications", null, null).body();
        assertThat(feed).contains("New expense").contains("Water — Rs 5 · Training 1 · paid by Babar");

        // Typing on, or saving again, updates that notification instead of adding more.
        send("PUT", "/api/ledger", ledger(exp("e1", "Water", "500")), admin);
        send("PUT", "/api/ledger", ledger(exp("e1", "Water", "500")), admin);
        feed = send("GET", "/api/notifications", null, null).body();
        assertThat(feed).contains("Water — Rs 500").doesNotContain("Rs 5 ·");
        assertThat(feed.split("\"id\"").length - 1).isEqualTo(1);

        // A second expense adds one; deleting the first removes its notification.
        send("PUT", "/api/ledger", ledger(exp("e1", "Water", "500") + "," + exp("e2", "Balls", "1200")), admin);
        assertThat(send("GET", "/api/notifications", null, null).body().split("\"id\"").length - 1).isEqualTo(2);
        send("PUT", "/api/ledger", ledger(exp("e2", "Balls", "1200")), admin);
        feed = send("GET", "/api/notifications", null, null).body();
        assertThat(feed).contains("Balls").doesNotContain("Water");

        // Viewers can't save, and a bulk change (like restoring a backup) stays quiet.
        assertThat(send("PUT", "/api/ledger", ledger(exp("e9", "X", "1")), null).statusCode()).isEqualTo(403);
        StringBuilder many = new StringBuilder(exp("e2", "Balls", "1200"));
        for (int i = 0; i < 6; i++) many.append(",").append(exp("b" + i, "Item" + i, "10"));
        send("PUT", "/api/ledger", ledger(many.toString()), admin);
        assertThat(send("GET", "/api/notifications", null, null).body()).doesNotContain("Item0");
    }

    @Test
    void testMatchesAreFlaggedAndNeverFinished() throws Exception {
        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body()
                .replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        String body = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"x1\":true}}],\"trainings\":[{\"id\":\"t1\"}],"
                + "\"matches\":[{\"id\":\"x1\",\"test\":true,\"finished\":true},{\"id\":\"m1\",\"finished\":true}]}";
        assertThat(send("PUT", "/api/ledger", body, admin).statusCode()).isEqualTo(200);
        String loaded = send("GET", "/api/ledger", null, null).body();
        assertThat(loaded).contains("{\"id\":\"x1\",\"date\":\"\",\"opponent\":\"\",\"venue\":\"\",\"finished\":false,\"test\":true,\"time\":\"\",\"announced\":false,\"result\":\"\",\"twelfthPlayed\":false,\"extras\":\"\"}")
                .contains("\"id\":\"m1\",\"date\":\"\",\"opponent\":\"\",\"venue\":\"\",\"finished\":true,\"test\":false");
        // The kick-off time is kept when valid, blanked when not, and kept when a save doesn't mention it.
        send("PUT", "/api/ledger", body.replace("\"id\":\"m1\",", "\"id\":\"m1\",\"time\":\"14:30\","), admin);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"time\":\"14:30\"");
        send("PUT", "/api/ledger", body.replace("\"id\":\"m1\",", "\"id\":\"m1\",\"time\":\"25:99\","), admin);
        assertThat(send("GET", "/api/ledger", null, null).body()).doesNotContain("25:99").doesNotContain("14:30");
        send("PUT", "/api/ledger", body.replace("\"id\":\"m1\",", "\"id\":\"m1\",\"time\":\"09:05\","), admin);
        send("PUT", "/api/ledger", body, admin);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"time\":\"09:05\"");
        // A save that doesn't mention the flag keeps it.
        send("PUT", "/api/ledger", body.replace("\"test\":true,", ""), admin);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"finished\":false,\"test\":true");
    }
}

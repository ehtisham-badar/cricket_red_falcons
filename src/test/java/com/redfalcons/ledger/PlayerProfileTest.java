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
        "spring.datasource.url=jdbc:h2:mem:profiletest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class PlayerProfileTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String token, String viewer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Admin-Token", token);
        if (viewer != null) b.header("X-Viewer", viewer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> sendBytes(String method, String path, byte[] body, String token, String viewer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "image/jpeg")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) b.header("X-Admin-Token", token);
        if (viewer != null) b.header("X-Viewer", viewer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void profileDetailsAndPhotos() throws Exception {
        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body()
                .replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        String saved = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{},\"battingHand\":\"left\",\"bowlingArm\":\"right\","
                + "\"bowlingStyle\":\"Off spin\",\"keeper\":true,\"captain\":true,\"viceCaptain\":false,\"jersey\":\"56x\"},"
                + "{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}],\"matches\":[]}";
        assertThat(send("PUT", "/api/ledger", saved, admin, null).statusCode()).isEqualTo(200);
        String viewer = send("GET", "/api/ledger", null, null, null).body();
        assertThat(viewer).contains("\"battingHand\":\"left\"").contains("\"bowlingArm\":\"right\"").contains("\"bowlingStyle\":\"Off spin\"")
                .contains("\"keeper\":true").contains("\"captain\":true").contains("\"jersey\":\"56\"").contains("\"photoAt\":\"\"");

        // A save from an older app (no profile fields) keeps the stored profile; bad values are cleaned.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}", admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null, null).body()).contains("\"battingHand\":\"left\"").contains("\"jersey\":\"56\"");
        send("PUT", "/api/ledger", saved.replace("\"left\"", "\"sideways\""), admin, null);
        assertThat(send("GET", "/api/ledger", null, null, null).body()).doesNotContain("sideways");

        // A player sets their own batting hand, bowling arm and style; captain and keeper stay as the admin set them.
        String mine = "{\"battingHand\":\"right\",\"bowlingArm\":\"left\",\"bowlingStyle\":\"Leg spin\"}";
        assertThat(send("PUT", "/api/players/p1/profile", mine, null, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/players/p1/profile", mine, null, "p2").statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/players/p1/profile", mine.replace("Leg spin", "Googly"), null, "p1").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/players/p1/profile", mine, null, "p1").statusCode()).isEqualTo(200);
        String after = send("GET", "/api/ledger", null, null, null).body();
        assertThat(after).contains("\"battingHand\":\"right\"").contains("\"bowlingArm\":\"left\"").contains("\"bowlingStyle\":\"Leg spin\"")
                .contains("\"captain\":true").contains("\"keeper\":true").contains("\"jersey\":\"56\"");
        assertThat(send("PUT", "/api/players/p2/profile", mine, admin, null).statusCode()).isEqualTo(200);

        // Photos: only the admin, or a viewer for their own player; real image bytes only.
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        assertThat(sendBytes("PUT", "/api/players/p1/photo", jpeg, null, null).statusCode()).isEqualTo(403);
        assertThat(sendBytes("PUT", "/api/players/p1/photo", jpeg, null, "p2").statusCode()).isEqualTo(403);
        assertThat(sendBytes("PUT", "/api/players/p1/photo", "not an image".getBytes(), null, "p1").statusCode()).isEqualTo(400);
        assertThat(sendBytes("PUT", "/api/players/nobody/photo", jpeg, admin, null).statusCode()).isEqualTo(403);
        assertThat(sendBytes("PUT", "/api/players/p1/photo", jpeg, null, "p1").statusCode()).isEqualTo(200);
        assertThat(sendBytes("PUT", "/api/players/p2/photo", jpeg, admin, null).statusCode()).isEqualTo(200);

        HttpResponse<byte[]> got = sendBytes("GET", "/api/players/p1/photo", null, null, null);
        assertThat(got.statusCode()).isEqualTo(200);
        assertThat(got.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(got.body()).isEqualTo(jpeg);
        String ledger = send("GET", "/api/ledger", null, null, null).body();
        assertThat(ledger).doesNotContain("\"photoAt\":\"\"");

        // Removing it, or removing the player, takes the picture away.
        assertThat(sendBytes("DELETE", "/api/players/p1/photo", null, null, "p1").statusCode()).isEqualTo(200);
        assertThat(sendBytes("GET", "/api/players/p1/photo", null, null, null).statusCode()).isEqualTo(404);
        send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}", admin, null);
        assertThat(sendBytes("GET", "/api/players/p2/photo", null, null, null).statusCode()).isEqualTo(404);
    }
}

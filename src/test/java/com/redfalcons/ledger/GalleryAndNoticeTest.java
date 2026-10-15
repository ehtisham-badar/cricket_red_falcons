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
        "spring.datasource.url=jdbc:h2:mem:gallerytest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class GalleryAndNoticeTest {

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

    private HttpResponse<String> upload(String path, byte[] bytes, String admin, String viewer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "image/jpeg").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (admin != null) b.header("X-Admin-Token", admin);
        if (viewer != null) b.header("X-Viewer", viewer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll(".*\"" + name + "\":\"([^\"]*)\".*", "$1");
    }

    private static String ledger(boolean announced) {
        return "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}],"
                + "\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}],\"matches\":[{\"id\":\"m1\",\"date\":\"2030-05-04\",\"opponent\":\"Raptors\","
                + "\"venue\":\"Park\",\"announced\":" + announced + "}]}";
    }

    @Test
    void announcingTheXiRaisesANoticeAndMatchPhotosHaveOwners() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", ledger(false), admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications", null, null, null).body()).doesNotContain("Playing XI announced");

        // Announcing the XI tells everyone, once; saving again changes nothing.
        assertThat(send("PUT", "/api/ledger", ledger(true), admin, null).statusCode()).isEqualTo(200);
        String feed = send("GET", "/api/notifications", null, null, null).body();
        assertThat(feed).contains("Playing XI announced").contains("Match 1: Red Falcons v Raptors").contains("\"kind\":\"notice\"")
                .contains("\"matchId\":\"m1\"");
        send("PUT", "/api/ledger", ledger(true), admin, null);
        String again = send("GET", "/api/notifications", null, null, null).body();
        assertThat(again.split("Playing XI announced", -1).length - 1).isEqualTo(1);

        // Photos: needs to be someone; real pictures only; owners and the admin can remove.
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        assertThat(upload("/api/matches/m1/photos", jpeg, null, null).statusCode()).isEqualTo(403);
        assertThat(upload("/api/matches/m1/photos", jpeg, null, "nobody").statusCode()).isEqualTo(403);
        assertThat(upload("/api/matches/m1/photos", "not an image".getBytes(), null, "p1").statusCode()).isEqualTo(400);
        assertThat(upload("/api/matches/zzz/photos", jpeg, null, "p1").statusCode()).isEqualTo(404);
        HttpResponse<String> mine = upload("/api/matches/m1/photos", jpeg, null, "p1");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains("\"by\":\"Babar\"");
        String adminPhoto = field(upload("/api/matches/m1/photos", jpeg, admin, null).body(), "id");
        String list = send("GET", "/api/match-photos?matchId=m1", null, null, null).body();
        assertThat(list).contains("Babar").contains("Admin");
        assertThat(http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/match-photos/" + field(mine.body(), "id"))).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body()).isEqualTo(jpeg);
        assertThat(send("DELETE", "/api/match-photos/" + adminPhoto, null, null, "p1").statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/match-photos/" + field(mine.body(), "id"), null, null, "p2").statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/match-photos/" + field(mine.body(), "id"), null, null, "p1").statusCode()).isEqualTo(200);
        assertThat(send("DELETE", "/api/match-photos/" + adminPhoto, null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/match-photos", null, null, null).body()).isEqualTo("[]");

        // Photos go when their match is deleted.
        assertThat(upload("/api/matches/m1/photos", jpeg, null, "p2").statusCode()).isEqualTo(200);
        send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"trainings\":[{\"id\":\"t1\"}],\"matches\":[]}", admin, null);
        assertThat(send("GET", "/api/match-photos", null, null, null).body()).isEqualTo("[]");
    }
}

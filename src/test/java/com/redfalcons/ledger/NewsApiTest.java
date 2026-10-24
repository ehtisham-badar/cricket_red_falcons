package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** News: the HPL rules are added once; everyone reads; only the admin writes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:newstest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class NewsApiTest {

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

    @Test
    void rulesSeededOnceAndOnlyAdminWrites() throws Exception {
        String list = send("GET", "/api/news", null, null).body();
        assertThat(list).contains("HPL – Official Cricket Rules").contains("\"tag\":\"Rules\"").contains("\"pinned\":true")
                .contains("Each bowler can bowl a maximum of 4 overs");
        String rulesId = list.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");

        String draft = "{\"title\":\"Kit collection\",\"tag\":\"Team\",\"body\":\"Friday 6 pm at the ground.\"}";
        assertThat(send("POST", "/api/news", draft, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/news/" + rulesId, draft, "forged").statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/api/news/" + rulesId, null, null).statusCode()).isEqualTo(403);

        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body();
        String token = admin.replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        String created = send("POST", "/api/news", draft, token).body();
        assertThat(created).contains("\"title\":\"Kit collection\"").contains("\"pinned\":false");
        String newId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertThat(send("POST", "/api/news", "{\"title\":\" \",\"body\":\"\"}", token).statusCode()).isEqualTo(400);

        // Pinned posts come first, then newest.
        String after = send("GET", "/api/news", null, null).body();
        assertThat(after.indexOf("HPL – Official Cricket Rules")).isLessThan(after.indexOf("Kit collection"));

        // Unpinning only flips the pin: no "edited" time, and only the admin can do it.
        assertThat(send("PUT", "/api/news/" + rulesId + "/pin", "{\"pinned\":false}", null).statusCode()).isEqualTo(403);
        String unpinned = send("PUT", "/api/news/" + rulesId + "/pin", "{\"pinned\":false}", token).body();
        assertThat(unpinned).contains("\"pinned\":false").contains("Each bowler can bowl a maximum of 4 overs");
        assertThat(unpinned.replaceAll(".*\"createdAt\":\"([^\"]+)\".*", "$1"))
                .isEqualTo(unpinned.replaceAll(".*\"updatedAt\":\"([^\"]+)\".*", "$1"));
        String reordered = send("GET", "/api/news", null, null).body();
        assertThat(reordered.indexOf("Kit collection")).isLessThan(reordered.indexOf("HPL – Official Cricket Rules"));
        assertThat(send("PUT", "/api/news/nope/pin", "{\"pinned\":true}", token).statusCode()).isEqualTo(404);

        assertThat(send("PUT", "/api/news/" + newId, "{\"title\":\"Kit collection moved\",\"tag\":\"Team\",\"body\":\"Saturday 5 pm.\",\"pinned\":true}", token).body())
                .contains("Kit collection moved").contains("\"pinned\":true");
        assertThat(send("DELETE", "/api/news/" + rulesId, null, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/news", null, null).body()).doesNotContain("Official Cricket Rules").contains("Kit collection moved");
    }
}

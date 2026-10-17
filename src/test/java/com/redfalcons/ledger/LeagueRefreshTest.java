package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Only the admin can force a league refresh. (No source URL here, so nothing is fetched.) */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:leaguerefresh",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.hpl-url="
})
class LeagueRefreshTest {

    @LocalServerPort
    int port;

    @Test
    void onlyTheAdminCanRefresh() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest anon = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hpl/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(http.send(anon, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);

        String login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"test-key\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String token = login.replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        HttpResponse<String> ok = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hpl/refresh"))
                .header("X-Admin-Token", token).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"available\":false");
    }
}

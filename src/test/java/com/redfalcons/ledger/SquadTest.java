package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** HPL squads: everyone reads them, only the admin saves them, and team names match however they're written. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:squads",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.hpl-url="
})
class SquadTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> put(String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hpl/squads"))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Admin-Token", token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String list() throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hpl/squads")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void onlyTheAdminSavesAndNamesMatch() throws Exception {
        assertThat(put(null, "{\"team\":\"Raptors\",\"players\":[\"Ali\"]}").statusCode()).isEqualTo(403);

        String login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"test-key\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String token = login.replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");

        HttpResponse<String> saved = put(token, "{\"team\":\"Rage Rovers\",\"players\":[\" Ali  Khan (c) - Batsman \",\"\",\"ali khan (C) - batsman\",\"Bilal (wk)\"]}");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.body()).contains("\"key\":\"ragerovers\"").contains("[\"Ali Khan (c) - Batsman\",\"Bilal (wk)\"]");

        // Crickslab writes it "RAGEROVERS": same squad, replaced rather than duplicated.
        put(token, "{\"team\":\"RAGEROVERS\",\"players\":[\"Ali Khan (c)\",\"Bilal (wk)\",\"Hamza\"]}");
        assertThat(list()).contains("\"Hamza\"").doesNotContain("Batsman");
        assertThat(list().split("\"key\"").length - 1).isEqualTo(1);

        // An empty list removes the squad.
        put(token, "{\"team\":\"Rage Rovers\",\"players\":[]}");
        assertThat(list()).isEqualTo("[]");
    }
}

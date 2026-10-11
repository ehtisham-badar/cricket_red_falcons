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
        "spring.datasource.url=jdbc:h2:mem:test",
        "ledger.admin-key=test-key"
})
class LedgerApiTest {

    private static final String STATE = """
            {"players":[{"id":"p1","name":"Babar","role":"Batsman + Captain","paid":"500","att":{"t1":true,"m1":true,"zz":true}}],
             "amounts":{"t1":"1500","m1":"3900"},
             "trainings":[{"id":"t1","desc":"Nets"}],
             "settings":{"currency":"PKR ","maxPerMatch":11,"roundUp":false}}
            """;

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
    void viewersCannotWriteAndAdminCan() throws Exception {
        String viewer = send("POST", "/api/login", "{\"name\":\"Ali\"}", null).body();
        assertThat(viewer).contains("\"admin\":false").contains("\"token\":null");
        assertThat(send("PUT", "/api/ledger", STATE, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/ledger", STATE, "forged").statusCode()).isEqualTo(403);

        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body();
        assertThat(admin).contains("\"admin\":true");
        String token = admin.replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");

        HttpResponse<String> saved = send("PUT", "/api/ledger", STATE, token);
        assertThat(saved.statusCode()).isEqualTo(200);

        String loaded = send("GET", "/api/ledger", null, null).body();
        assertThat(loaded)
                .contains("\"name\":\"Babar\"")
                .contains("\"m1\":true")
                .doesNotContain("\"zz\"")
                .contains("\"currency\":\"PKR \"")
                .contains("\"desc\":\"Nets\"");

        // Replacing again with fewer players removes the old ones and their attendance.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .doesNotContain("Babar")
                .contains("\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}]");
    }
}

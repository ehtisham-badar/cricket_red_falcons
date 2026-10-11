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
        "ledger.admin-key=test-key",
        "ledger.sync-from=" // never copy live data into tests, even if config/ enables it locally
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

        // A version 2 file (amounts only) is read as one fund-paid expense per day.
        assertThat(loaded).contains("\"t1\":[{\"id\":\"legacy-t1\",\"item\":\"Amount spent\",\"amount\":\"1500\",\"paidBy\":\"\"}]");

        String v3 = """
                {"players":[{"id":"p1","name":"Babar","paid":"1500","tournament":true,"att":{"t1":true}},
                            {"id":"p2","name":"Rizwan","paid":"2000","tournament":false,"att":{}}],
                 "trainings":[{"id":"t1","desc":""}],
                 "expenses":{"t1":[{"id":"e1","item":"Water","amount":"500","paidBy":"p2"},
                                   {"id":"e2","item":"Balls","amount":"1000","paidBy":"ghost"}]},
                 "tournament":{"fee":"65000"}}
                """;
        assertThat(send("PUT", "/api/ledger", v3, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .contains("\"tournament\":{\"fee\":\"65000\"}")
                .contains("\"name\":\"Rizwan\",\"role\":\"\",\"paid\":\"2000\",\"tournament\":false")
                .contains("{\"id\":\"e1\",\"item\":\"Water\",\"amount\":\"500\",\"paidBy\":\"p2\"}")
                // paidBy pointing at an unknown player falls back to the team fund
                .contains("{\"id\":\"e2\",\"item\":\"Balls\",\"amount\":\"1000\",\"paidBy\":\"\"}")
                .contains("\"amounts\":{\"t1\":\"1500\"}")
                .doesNotContain("legacy-t1");

        // Payments: the player's paid total is their sum; a bare "paid" (older files) becomes one undated payment.
        String withPayments = """
                {"players":[{"id":"p1","name":"Babar","att":{},"payments":[
                                {"id":"x1","date":"2026-10-12","amount":"1500","method":"Cash","note":"first"},
                                {"id":"x2","date":"not-a-date","amount":"500.5","method":"JazzCash","note":""}]},
                            {"id":"p2","name":"Rizwan","paid":"2000","att":{}}]}
                """;
        assertThat(send("PUT", "/api/ledger", withPayments, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .contains("\"paid\":\"2000.5\"")
                .contains("{\"id\":\"x1\",\"date\":\"2026-10-12\",\"amount\":\"1500\",\"method\":\"Cash\",\"note\":\"first\"}")
                .contains("{\"id\":\"x2\",\"date\":\"\",\"amount\":\"500.5\"")
                .contains("\"payments\":[{\"id\":\"legacy-pay-p2\",\"date\":\"\",\"amount\":\"2000\",\"method\":\"\",\"note\":\"Paid before payment history\"}]");

        // Replacing again with fewer players removes the old ones and their attendance.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .doesNotContain("Babar")
                .contains("\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}]")
                .contains("\"expenses\":{}");
    }
}

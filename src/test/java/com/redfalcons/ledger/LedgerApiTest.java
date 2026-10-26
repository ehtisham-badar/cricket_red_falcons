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
             "matches":[{"id":"m1","date":"2026-10-24","opponent":"Lahore Lions","venue":"","finished":true},{"id":"m2","date":"not-a-date"}],
             "settings":{"currency":"PKR ","maxPerMatch":11,"roundUp":false}}
            """;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> viewerGet(String viewer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ledger"))
                .header("X-Viewer", viewer).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

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
                // Payment account details stay null until the admin sets them.
                .contains("\"payBank\":null,\"payTitle\":null,\"payAccount\":null")
                .contains("\"desc\":\"Nets\"")
                .contains("\"matches\":[{\"id\":\"m1\",\"date\":\"2026-10-24\",\"opponent\":\"Lahore Lions\",\"venue\":\"\",\"finished\":true,\"test\":false,\"time\":\"\",\"announced\":false,\"result\":\"\",\"twelfthPlayed\":false,\"extras\":\"\",\"twelfthShares\":false},{\"id\":\"m2\",\"date\":\"\",\"opponent\":\"\",\"venue\":\"\",\"finished\":false,\"test\":false,\"time\":\"\",\"announced\":false,\"result\":\"\",\"twelfthPlayed\":false,\"extras\":\"\",\"twelfthShares\":false}]");

        // A version 2 file (amounts only) is read as one fund-paid expense per day.
        assertThat(loaded).contains("\"t1\":[{\"id\":\"legacy-t1\",\"item\":\"Amount spent\",\"amount\":\"1500\",\"paidBy\":\"\",\"sharedBy\":[]}]");

        String v3 = """
                {"players":[{"id":"p1","name":"Babar","paid":"1500","tournament":true,"att":{"t1":true}},
                            {"id":"p2","name":"Rizwan","paid":"2000","tournament":false,"att":{}}],
                 "trainings":[{"id":"t1","desc":""}],
                 "expenses":{"t1":[{"id":"e1","item":"Water","amount":"500","paidBy":"p2","sharedBy":["p2","ghost","p2"]},
                                   {"id":"e2","item":"Balls","amount":"1000","paidBy":"ghost"}]},
                 "tournament":{"fee":"65000"}}
                """;
        assertThat(send("PUT", "/api/ledger", v3, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .contains("\"tournament\":{\"name\":\"\",\"fee\":\"65000\",\"advance\":\"\",\"payments\":[],\"matchCount\":5}")
                .contains("\"name\":\"Rizwan\",\"role\":\"\",\"paid\":\"2000\",\"tournament\":false")
                // sharedBy keeps only real players, once each
                .contains("{\"id\":\"e1\",\"item\":\"Water\",\"amount\":\"500\",\"paidBy\":\"p2\",\"sharedBy\":[\"p2\"]}")
                // paidBy pointing at an unknown player falls back to the team fund
                .contains("{\"id\":\"e2\",\"item\":\"Balls\",\"amount\":\"1000\",\"paidBy\":\"\",\"sharedBy\":[]}")
                .contains("\"amounts\":{\"t1\":\"1500\"}")
                .doesNotContain("legacy-t1");

        // Charges on chosen players (a shirt order) keep only real players, once each; a save without them keeps them.
        String withCharges = v3.replace("\"tournament\":{\"fee\":\"65000\"}",
                "\"tournament\":{\"fee\":\"65000\"},\"charges\":[{\"id\":\"c1\",\"title\":\" Shirt \",\"amount\":\"2500\",\"players\":[\"p2\",\"ghost\",\"p2\",\"p1\"]}]");
        assertThat(send("PUT", "/api/ledger", withCharges, token).statusCode()).isEqualTo(200);
        String charged = "\"charges\":[{\"id\":\"c1\",\"title\":\"Shirt\",\"amount\":\"2500\",\"players\":[\"p2\",\"p1\"]}]";
        assertThat(send("GET", "/api/ledger", null, null).body()).contains(charged);
        assertThat(send("PUT", "/api/ledger", v3, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains(charged);

        // League fee name, advance, transfers to the tournament management and the payment account round-trip.
        String withManagement = """
                {"players":[],"tournament":{"name":"Hundred Premier League","fee":"65000","advance":"1200","matchCount":6,"payments":[
                    {"id":"m1","date":"2026-10-05","amount":"20000","method":"Bank transfer","note":"First instalment"}]},
                 "settings":{"payBank":"NayaPay","payTitle":"Team Admin","payAccount":"03001234567"}}
                """;
        assertThat(send("PUT", "/api/ledger", withManagement, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains(
                "\"tournament\":{\"name\":\"Hundred Premier League\",\"fee\":\"65000\",\"advance\":\"1200\",\"payments\":[{\"id\":\"m1\",\"date\":\"2026-10-05\",\"amount\":\"20000\",\"method\":\"Bank transfer\",\"note\":\"First instalment\"}],\"matchCount\":6}")
                .contains("\"payBank\":\"NayaPay\",\"payTitle\":\"Team Admin\",\"payAccount\":\"03001234567\"");

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

        // With viewer access "own", a viewer only gets their own payments; the admin still gets all.
        String privateLedger = """
                {"players":[{"id":"p1","name":"Babar","att":{},"payments":[{"id":"x1","date":"","amount":"700","method":"","note":""}]},
                            {"id":"p2","name":"Rizwan","att":{},"payments":[{"id":"x2","date":"","amount":"900","method":"","note":""}]}],
                 "settings":{"viewerAccess":"own"}}
                """;
        assertThat(send("PUT", "/api/ledger", privateLedger, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"viewerAccess\":\"own\"").doesNotContain("\"700\"").doesNotContain("\"900\"");
        assertThat(viewerGet("p1").body()).contains("\"amount\":\"700\"").doesNotContain("\"900\"");
        assertThat(send("GET", "/api/ledger", null, token).body()).contains("\"700\"").contains("\"900\"");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[],\"settings\":{\"viewerAccess\":\"all\"}}", token).statusCode()).isEqualTo(200);

        // Phone numbers reach only the admin, and a save without them keeps the stored ones.
        String withPhone = """
                {"players":[{"id":"p1","name":"Babar","att":{},"payments":[],"phone":"03119876543"}]}
                """;
        assertThat(send("PUT", "/api/ledger", withPhone, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).doesNotContain("03119876543").contains("\"phone\":null");
        assertThat(send("GET", "/api/ledger", null, token).body()).contains("\"phone\":\"03119876543\"");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, token).body()).contains("\"phone\":\"03119876543\"");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{},\"phone\":\"\"}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, token).body()).contains("\"phone\":\"\"");

        // A 12th man is kept per match; one who is also playing that match, or unknown, is dropped.
        String withTwelfth = """
                {"players":[{"id":"p1","name":"Babar","att":{"m1":true}},{"id":"p2","name":"Rizwan","att":{}}],
                 "twelfth":{"m1":"p2","m2":"p1","m3":"ghost","t1":"p2"}}
                """;
        assertThat(send("PUT", "/api/ledger", withTwelfth, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"twelfth\":{\"m1\":\"p2\",\"m2\":\"p1\"}");
        // A save without twelfth keeps them; one where the 12th man now plays drops that match.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true,\"m2\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"twelfth\":{\"m1\":\"p2\"}");

        // A batting order keeps only that match's players, once each; a save without it keeps it.
        String withOrder = """
                {"players":[{"id":"p1","name":"Babar","att":{"m1":true}},{"id":"p2","name":"Rizwan","att":{"m1":true}},
                            {"id":"p3","name":"Shaheen","att":{}}],
                 "battingOrder":{"m1":["p2","p3","p1","p2"],"t1":["p1"]}}
                """;
        assertThat(send("PUT", "/api/ledger", withOrder, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"battingOrder\":{\"m1\":[\"p2\",\"p1\"]}");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{\"m1\":true}}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"battingOrder\":{\"m1\":[\"p2\",\"p1\"]}");

        // Match stats: admin only, kept per match and player; negatives clamp to 0, unknown players drop,
        // and a save without stats keeps them.
        String withStats = """
                {"players":[{"id":"p1","name":"Babar","att":{"m1":true}}],
                 "matches":[{"id":"m1","date":"2026-10-24"}],
                 "stats":{"m1":{"p1":{"balls":18,"ones":5,"fours":3,"sixes":1,"out":true,"bowlBalls":10,"bowlRuns":14,"wickets":2,"catches":1,"drops":-3,"wides":2,"noBalls":1},
                                "ghost":{"balls":4}}}}
                """;
        assertThat(send("PUT", "/api/ledger", withStats, token).statusCode()).isEqualTo(200);
        String statsJson = "\"stats\":{\"m1\":{\"p1\":{\"balls\":18,\"ones\":5,\"twos\":0,\"threes\":0,\"fours\":3,\"sixes\":1,\"out\":true,"
                + "\"bowlBalls\":10,\"bowlRuns\":14,\"wickets\":2,\"catches\":1,\"drops\":0,\"misfields\":0,\"goodFielding\":0,\"wides\":2,\"noBalls\":1,\"dismissal\":\"\",\"bowler\":\"\",\"fielder\":\"\",\"fowScore\":null,\"fowOver\":\"\"}}}";
        assertThat(send("GET", "/api/ledger", null, token).body()).contains(statsJson).doesNotContain("ghost");
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"stats\":{}");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, token).body()).contains(statsJson);
        // With stats shown to everyone, viewers get them too.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}}],\"settings\":{\"statsVisibility\":\"all\"}}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains(statsJson).contains("\"statsVisibility\":\"all\"").contains("\"daysVisibility\":\"admin\"");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}}],\"settings\":{\"statsVisibility\":\"admin\"}}", token).statusCode()).isEqualTo(200);
        // The Summary cards: Next match is on and the fixtures calendar is off until the admin says otherwise.
        String cards = send("GET", "/api/ledger", null, null).body();
        assertThat(cards).contains("\"showNextMatch\":true").contains("\"showFixtures\":false").contains("\"adminShowFixtures\":true");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"settings\":{\"showNextMatch\":false,\"showFixtures\":true}}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"showNextMatch\":false").contains("\"showFixtures\":true");
        // The admin can hide the fixtures calendar from themselves too; it doesn't change what players see.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"settings\":{\"adminShowFixtures\":false}}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"adminShowFixtures\":false").contains("\"showFixtures\":true");
        send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{}}],\"settings\":{\"currency\":\"Rs \"}}", token);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"showNextMatch\":false").contains("\"showFixtures\":true");
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"stats\":{}");

        // Removing a match drops its attendance, 12th man and batting order.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{\"m1\":true}}],\"matches\":[]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .contains("\"matches\":[]").contains("\"twelfth\":{}").contains("\"battingOrder\":{}").doesNotContain("\"m1\":true");
        assertThat(send("GET", "/api/ledger", null, token).body()).contains("\"stats\":{}");

        // Season archive: admin-only to create/delete, a frozen copy that survives later changes;
        // viewers never get phone numbers from it.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p9\",\"name\":\"Fakhar\",\"att\":{},\"phone\":\"03001112223\"}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/seasons", "{\"name\":\"Spring Cup\"}", null).statusCode()).isEqualTo(403);
        String archived = send("POST", "/api/seasons", "{\"name\":\"Spring Cup\"}", token).body();
        assertThat(archived).contains("\"name\":\"Spring Cup\"");
        String seasonId = archived.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/seasons", null, null).body()).contains("\"id\":\"" + seasonId + "\"");
        assertThat(send("GET", "/api/seasons/" + seasonId, null, token).body()).contains("Fakhar").contains("03001112223");
        assertThat(send("GET", "/api/seasons/" + seasonId, null, null).body()).contains("Fakhar").doesNotContain("03001112223");
        assertThat(send("DELETE", "/api/seasons/" + seasonId, null, null).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/api/seasons/" + seasonId, null, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/seasons/" + seasonId, null, token).statusCode()).isEqualTo(404);

        // Announcing a match's Playing XI is kept, also by saves that don't send the flag.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[],\"matches\":[{\"id\":\"mA\",\"announced\":true}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"id\":\"mA\"").contains("\"announced\":true");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[],\"matches\":[{\"id\":\"mA\"}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"announced\":true");
        assertThat(send("PUT", "/api/ledger", "{\"players\":[],\"matches\":[{\"id\":\"mA\",\"announced\":false,\"result\":\"\",\"twelfthPlayed\":false,\"extras\":\"\",\"twelfthShares\":false}]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"announced\":false");

        // A 12th man who only fields isn't flagged; once he bats or bowls, every viewer is told (stats or not).
        String tw = "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"mT\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{}}],"
                + "\"matches\":[{\"id\":\"mT\"}],\"twelfth\":{\"mT\":\"p2\"},\"stats\":{\"mT\":{\"p2\":{%s}}},\"settings\":{\"statsVisibility\":\"admin\"}}";
        assertThat(send("PUT", "/api/ledger", String.format(tw, "\"catches\":2"), token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"twelfthPlayed\":false").contains("\"stats\":{}");
        assertThat(send("PUT", "/api/ledger", String.format(tw, "\"balls\":6,\"fours\":1"), token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body()).contains("\"twelfthPlayed\":true").contains("\"stats\":{}");

        // Replacing again with fewer players removes the old ones and their attendance.
        assertThat(send("PUT", "/api/ledger", "{\"players\":[]}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/ledger", null, null).body())
                .doesNotContain("Babar")
                .contains("\"trainings\":[{\"id\":\"t1\",\"desc\":\"\"}]")
                .contains("\"expenses\":{}");
    }
}

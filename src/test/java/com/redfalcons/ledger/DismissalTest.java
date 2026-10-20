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
        "spring.datasource.url=jdbc:h2:mem:dismissaltest",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class DismissalTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String admin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (admin != null) b.header("X-Admin-Token", admin);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String ledger(String stats, String extras) {
        return "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar\",\"att\":{\"m1\":true}},{\"id\":\"p2\",\"name\":\"Rizwan\",\"att\":{\"m1\":true}}],"
                + "\"trainings\":[{\"id\":\"t1\"}],\"matches\":[{\"id\":\"m1\",\"date\":\"2026-10-17\",\"opponent\":\"Raptors\",\"finished\":true"
                + (extras == null ? "" : ",\"extras\":\"" + extras + "\"") + "}],\"stats\":" + stats + "}";
    }

    @Test
    void howABatterGotOutAndTheExtrasAreKept() throws Exception {
        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body().replaceAll(".*\"token\":\"([^\"]*)\".*", "$1");
        String stats = "{\"m1\":{\"p1\":{\"balls\":20,\"ones\":8,\"fours\":3,\"out\":true,\"dismissal\":\"caught\",\"bowler\":\"Hamza Ali\",\"fielder\":\"Zain\","
                + "\"fowScore\":45,\"fowOver\":\"6.2\"},"
                + "\"p2\":{\"balls\":10,\"ones\":4,\"out\":false,\"dismissal\":\"bowled\",\"bowler\":\"Ignored\",\"fowScore\":12}}}";
        assertThat(send("PUT", "/api/ledger", ledger(stats, "1,2,5,1"), admin).statusCode()).isEqualTo(200);
        String body = send("GET", "/api/ledger", null, admin).body();
        assertThat(body).contains("\"dismissal\":\"caught\",\"bowler\":\"Hamza Ali\",\"fielder\":\"Zain\",\"fowScore\":45,\"fowOver\":\"6.2\"")
                .contains("\"extras\":\"1,2,5,1\"");
        // A batter who is not out has no dismissal, whatever was sent.
        assertThat(body).doesNotContain("Ignored");

        // An unknown dismissal is dropped; bad extras are cleared; a save without them keeps what was stored.
        String odd = "{\"m1\":{\"p1\":{\"balls\":20,\"ones\":8,\"out\":true,\"dismissal\":\"exploded\",\"bowler\":\"X\"}}}";
        send("PUT", "/api/ledger", ledger(odd, "1,2,x,4"), admin);
        String again = send("GET", "/api/ledger", null, admin).body();
        assertThat(again).doesNotContain("exploded").contains("\"dismissal\":\"\"").contains("\"extras\":\"\"");
        send("PUT", "/api/ledger", ledger(stats, "0,3,0,2"), admin);
        send("PUT", "/api/ledger", ledger(stats, null), admin);
        assertThat(send("GET", "/api/ledger", null, admin).body()).contains("\"extras\":\"0,3,0,2\"");

        // "Share the expenses with the 12th man" is kept per match, and a save that doesn't mention it keeps it.
        String shared = ledger(stats, null).replace("\"finished\":true", "\"finished\":true,\"twelfthShares\":true");
        send("PUT", "/api/ledger", shared, admin);
        assertThat(send("GET", "/api/ledger", null, admin).body()).contains("\"twelfthShares\":true");
        send("PUT", "/api/ledger", ledger(stats, null), admin);
        assertThat(send("GET", "/api/ledger", null, admin).body()).contains("\"twelfthShares\":true");
        send("PUT", "/api/ledger", ledger(stats, null).replace("\"finished\":true", "\"finished\":true,\"twelfthShares\":false"), admin);
        assertThat(send("GET", "/api/ledger", null, admin).body()).contains("\"twelfthShares\":false");
    }
}

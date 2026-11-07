package com.redfalcons.ledger;

import com.redfalcons.ledger.service.Birthdays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Dates of birth on accounts: everyone may add one, the admin sees it, and squad players (only) get a birthday wish. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:birthdays",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.hpl-url="
})
class BirthdayTest {

    private static final ZoneId PAKISTAN = ZoneId.of("Asia/Karachi");

    @LocalServerPort
    int port;

    @Autowired
    Birthdays birthdays;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String admin, String user) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (admin != null) b.header("X-Admin-Token", admin);
        if (user != null) b.header("X-User-Token", user);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll("(?s).*?\"" + name + "\":\"([^\"]*)\".*", "$1");
    }

    @Test
    void everyoneCanAddADateOfBirthAndOnlySquadPlayersAreWished() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        send("PUT", "/api/ledger", "{\"players\":[{\"id\":\"p1\",\"name\":\"Babar Azam (Bobby)\",\"att\":{}}],\"trainings\":[],\"matches\":[],\"expenses\":{},"
                + "\"settings\":{\"currency\":\"Rs \",\"maxPerMatch\":11}}", admin, null);
        String babar = field(send("POST", "/api/account/signup", "{\"email\":\"babar@test.example\",\"password\":\"secret123\",\"name\":\"Babar Azam (Bobby)\"}", null, null).body(), "token");
        String guest = field(send("POST", "/api/account/signup", "{\"email\":\"guest@test.example\",\"password\":\"secret123\",\"name\":\"Some Guest\"}", null, null).body(), "token");

        LocalDate today = LocalDate.now(PAKISTAN);
        String born = today.minusYears(30).toString();
        // Not a date, or in the future, is refused; a real one is saved and can be cleared.
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"31-12-1990\"}", null, babar).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"" + today.plusDays(1) + "\"}", null, babar).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"" + born + "\"}", null, babar).body()).contains("\"dob\":\"" + born + "\"");
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"\"}", null, guest).body()).contains("\"dob\":\"\"");
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"" + born + "\"}", null, guest).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/account/dob", "{\"dob\":\"" + born + "\"}", null, null).statusCode()).isEqualTo(401);

        // The admin sees both on Accounts.
        assertThat(send("GET", "/api/account/users", null, admin, null).body().split("\"dob\":\"" + born + "\"", -1).length - 1).isEqualTo(2);

        // The team sees the squad player's birthday (no year); a guest sees none, and isn't in it.
        String list = send("GET", "/api/account/birthdays", null, null, babar).body();
        assertThat(list).contains("\"playerId\":\"p1\"").contains("\"inDays\":0").doesNotContain("Some Guest").doesNotContain(String.valueOf(today.minusYears(30).getYear()));
        assertThat(send("GET", "/api/account/birthdays", null, null, guest).body()).isEqualTo("[]");

        // Before 9 am nothing; after, one wish for Babar only, once.
        assertThat(birthdays.wishAt(today.atTime(8, 30).atZone(PAKISTAN))).isZero();
        assertThat(birthdays.wishAt(today.atTime(10, 0).atZone(PAKISTAN))).isEqualTo(1);
        assertThat(birthdays.wishAt(ZonedDateTime.now(PAKISTAN).withHour(23))).isZero();
        String feed = send("GET", "/api/notifications", null, null, babar).body();
        assertThat(feed).contains("Happy birthday, Babar!").contains("Babar Azam (Bobby)’s birthday today");
        assertThat(feed.split("Happy birthday", -1).length - 1).isEqualTo(1);
        assertThat(send("GET", "/api/notifications", null, null, guest).body()).isEqualTo("[]");
    }
}

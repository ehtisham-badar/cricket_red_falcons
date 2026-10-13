package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin two-factor: setup, codes, recovery codes, trusted browsers and sessions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:twofactor",
        "ledger.admin-key=test-key",
        "ledger.sync-from="
})
class TwoFactorTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body, String token, String device) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Admin-Token", token);
        if (device != null) b.header("X-Device", device);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        return json.replaceAll("(?s).*\"" + name + "\":\"([^\"]+)\".*", "$1");
    }

    private static int totp(String secret, long step) throws Exception {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream key = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : secret.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) { key.write(buffer >> (bits - 8)); bits -= 8; }
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.toByteArray(), "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int off = h[h.length - 1] & 0x0f;
        return (((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff)) % 1_000_000;
    }

    private static String code(String secret) throws Exception {
        return String.format("%06d", totp(secret, Instant.now().getEpochSecond() / 30));
    }

    @Test
    void twoFactorFlow() throws Exception {
        // Before two-factor: the key alone signs in; each sign-in gets its own token.
        String first = send("POST", "/api/login", "{\"name\":\"test-key\",\"label\":\"Laptop\"}", null, null).body();
        assertThat(first).contains("\"admin\":true");
        String token = field(first, "token");
        String other = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(other).isNotEqualTo(token);
        assertThat(send("GET", "/api/session", null, other, null).body()).contains("\"admin\":true");

        // Setup needs an admin session; a wrong code doesn't turn it on.
        assertThat(send("POST", "/api/2fa/setup", null, null, null).statusCode()).isEqualTo(403);
        String setup = send("POST", "/api/2fa/setup", null, token, null).body();
        String secret = field(setup, "secret");
        assertThat(field(setup, "uri")).startsWith("otpauth://totp/").contains("secret=" + secret);
        assertThat(send("POST", "/api/2fa/confirm", "{\"code\":\"000000\"}", token, null).statusCode()).isEqualTo(401);

        // Turning it on returns 8 recovery codes, trusts this browser and ends the other session.
        String confirmed = send("POST", "/api/2fa/confirm", "{\"code\":\"" + code(secret) + "\",\"label\":\"Laptop\"}", token, null).body();
        String[] recovery = confirmed.replaceAll("(?s).*\"recoveryCodes\":\\[([^\\]]*)\\].*", "$1").replace("\"", "").split(",");
        assertThat(recovery).hasSize(8);
        String device = field(confirmed, "device");
        assertThat(send("GET", "/api/session", null, other, null).body()).contains("\"admin\":false");
        assertThat(send("GET", "/api/session", null, token, null).body()).contains("\"admin\":true");
        assertThat(send("GET", "/api/2fa", null, token, device).body()).contains("\"enabled\":true").contains("\"recoveryLeft\":8").contains("\"current\":true");

        // Now the key alone only earns a challenge.
        String login = send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body();
        assertThat(login).contains("\"needCode\":true").contains("\"token\":null").contains("\"admin\":false");
        String challenge = field(login, "challenge");
        assertThat(send("POST", "/api/login/verify", "{\"challenge\":\"" + challenge + "\",\"code\":\"123456\"}", null, null).body())
                .contains("4 tries left");
        // The code used to turn it on can't be used again (same 30-second step).
        assertThat(send("POST", "/api/login/verify", "{\"challenge\":\"" + challenge + "\",\"code\":\"" + code(secret) + "\"}", null, null).statusCode())
                .isEqualTo(401);
        // A recovery code works, once.
        String verified = send("POST", "/api/login/verify", "{\"challenge\":\"" + challenge + "\",\"code\":\"" + recovery[0] + "\",\"trust\":false}", null, null).body();
        assertThat(verified).contains("\"admin\":true").contains("\"device\":null").contains("\"recoveryLeft\":7");
        String c2 = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "challenge");
        assertThat(send("POST", "/api/login/verify", "{\"challenge\":\"" + c2 + "\",\"code\":\"" + recovery[0] + "\"}", null, null).statusCode()).isEqualTo(401);
        // Five wrong tries end the challenge.
        for (int i = 0; i < 4; i++) send("POST", "/api/login/verify", "{\"challenge\":\"" + c2 + "\",\"code\":\"111111\"}", null, null);
        assertThat(send("POST", "/api/login/verify", "{\"challenge\":\"" + c2 + "\",\"code\":\"" + recovery[1] + "\"}", null, null).body()).contains("Too many tries");

        // The trusted browser skips the code.
        assertThat(send("POST", "/api/login", "{\"name\":\"test-key\",\"device\":\"" + device + "\"}", null, null).body())
                .contains("\"admin\":true").doesNotContain("needCode");
        assertThat(send("POST", "/api/login", "{\"name\":\"test-key\",\"device\":\"forged\"}", null, null).body()).contains("\"needCode\":true");

        // Revoking the browser brings the code back; signing out ends the session.
        String devices = send("GET", "/api/2fa", null, token, device).body();
        String deviceId = field(devices, "id");
        assertThat(send("DELETE", "/api/2fa/devices/" + deviceId, null, token, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/login", "{\"name\":\"test-key\",\"device\":\"" + device + "\"}", null, null).body()).contains("\"needCode\":true");
        send("POST", "/api/logout", null, token, null);
        assertThat(send("GET", "/api/session", null, token, null).body()).contains("\"admin\":false");

        // Turning it off needs a valid code.
        String fresh = field(send("POST", "/api/login/verify", "{\"challenge\":\""
                + field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "challenge")
                + "\",\"code\":\"" + recovery[2] + "\"}", null, null).body(), "token");
        assertThat(send("POST", "/api/2fa/disable", "{\"code\":\"999999\"}", fresh, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/2fa/disable", "{\"code\":\"" + recovery[3] + "\"}", fresh, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body()).contains("\"admin\":true");
        // Viewers are unaffected throughout.
        assertThat(send("POST", "/api/login", "{\"name\":\"Ali\"}", null, null).body()).contains("\"name\":\"Ali\"").contains("\"admin\":false");
    }
}

package com.redfalcons.ledger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Notifications: subscribe, send (admin only), a VAPID-signed empty push, then fetch what's waiting. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:pushtest",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.push-allow-http=true"
})
class PushApiTest {

    private static final KeyPair KEYS;
    private static final String PUBLIC_B64;
    private static final HttpServer PUSH_SERVICE;
    private static final CompletableFuture<String> AUTH_HEADER = new CompletableFuture<>();

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KEYS = gen.generateKeyPair();
            ECPublicKey pub = (ECPublicKey) KEYS.getPublic();
            byte[] point = new byte[65];
            point[0] = 4;
            System.arraycopy(fixed(pub.getW().getAffineX()), 0, point, 1, 32);
            System.arraycopy(fixed(pub.getW().getAffineY()), 0, point, 33, 32);
            PUBLIC_B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(point);
            // A stand-in push service that records the Authorization header of the push it receives.
            PUSH_SERVICE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PUSH_SERVICE.createContext("/push/device1", ex -> {
                AUTH_HEADER.complete(ex.getRequestHeaders().getFirst("Authorization"));
                ex.sendResponseHeaders(201, -1);
                ex.close();
            });
            PUSH_SERVICE.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] fixed(BigInteger v) {
        byte[] b = v.toByteArray();
        byte[] out = new byte[32];
        System.arraycopy(b, Math.max(0, b.length - 32), out, Math.max(0, 32 - b.length), Math.min(32, b.length));
        return out;
    }

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry r) {
        r.add("ledger.vapid-public", () -> PUBLIC_B64);
        r.add("ledger.vapid-private", () -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fixed(((ECPrivateKey) KEYS.getPrivate()).getS())));
    }

    @AfterAll
    static void stop() {
        PUSH_SERVICE.stop(0);
    }

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
    void subscribeSendAndFetch() throws Exception {
        assertThat(send("GET", "/api/push/key", null, null).body()).contains("\"enabled\":true").contains(PUBLIC_B64);

        String endpoint = "http://127.0.0.1:" + PUSH_SERVICE.getAddress().getPort() + "/push/device1";
        assertThat(send("POST", "/api/push/subscribe", "{\"endpoint\":\"" + endpoint + "\",\"playerId\":\"p1\"}", null).statusCode()).isEqualTo(200);

        String admin = send("POST", "/api/login", "{\"name\":\"test-key\"}", null).body();
        String token = admin.replaceAll(".*\"token\":\"([0-9a-f]+)\".*", "$1");
        String msg = "{\"title\":\"Match day\",\"body\":\"Report at 2:30 pm\",\"playerIds\":[\"p1\"]}";
        assertThat(send("POST", "/api/push/send", msg, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/push/send", msg, token).body()).contains("\"devices\":1");
        // Players not in the list get nothing.
        assertThat(send("POST", "/api/push/send", "{\"body\":\"x\",\"playerIds\":[\"p2\"]}", token).body()).contains("\"devices\":0");

        // The push carries a valid ES256 VAPID token signed with our key.
        String auth = AUTH_HEADER.get(10, TimeUnit.SECONDS);
        assertThat(auth).startsWith("vapid t=").contains(", k=" + PUBLIC_B64);
        String jwt = auth.substring("vapid t=".length(), auth.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(KEYS.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                .contains("\"aud\":\"http://127.0.0.1:" + PUSH_SERVICE.getAddress().getPort() + "\"");

        // The device fetches its message once.
        String pending = "/api/push/pending?endpoint=" + java.net.URLEncoder.encode(endpoint, StandardCharsets.UTF_8);
        assertThat(send("GET", pending, null, null).body()).contains("\"title\":\"Match day\"").contains("Report at 2:30 pm");
        assertThat(send("GET", pending, null, null).body()).isEqualTo("[]");

        // Unsubscribed devices get nothing.
        assertThat(send("POST", "/api/push/unsubscribe", "{\"endpoint\":\"" + endpoint + "\"}", null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/push/send", "{\"body\":\"y\"}", token).body()).contains("\"devices\":0");
        assertThat(Arrays.asList(parts)).hasSize(3);
    }
}

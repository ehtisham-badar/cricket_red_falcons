package com.redfalcons.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Player accounts end to end: sign-up and the squad name match, sign-in, resets, the admin's list, and biometrics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:accounts",
        "ledger.admin-key=test-key",
        "ledger.sync-from=",
        "ledger.hpl-url=",
        "ledger.webauthn.rp-id=localhost",
        "ledger.webauthn.origins=http://localhost:3000"
})
class AccountTest {

    private static final String SQUAD = "{\"players\":[{\"id\":\"p1\",\"name\":\"Ehtisham Badar\",\"att\":{}},{\"id\":\"p2\",\"name\":\"Nabeel Yousaf\",\"att\":{}},"
            + "{\"id\":\"p3\",\"name\":\"Nabeel Sohail\",\"att\":{}},{\"id\":\"p4\",\"name\":\"Talha Ramzan\",\"att\":{}},{\"id\":\"p5\",\"name\":\"Haider\",\"att\":{}},{\"id\":\"p6\",\"name\":\"Shehroz Khan\",\"att\":{}}]}";

    @LocalServerPort
    int port;

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

    private HttpResponse<String> signup(String email, String password, String name) throws Exception {
        return send("POST", "/api/account/signup", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"" + name + "\",\"label\":\"Test\"}", null, null);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return send("POST", "/api/account/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"remember\":true,\"label\":\"Test\"}", null, null);
    }

    @Test
    void signUpMatchesTheSquadAndSignInWorks() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", SQUAD, admin, null).statusCode()).isEqualTo(200);

        // The same name as in the squad (any case or spacing) links straight away, with the squad's spelling.
        HttpResponse<String> exact = signup("Roxy@Example.com ", "secret123", "  ehtisham   BADAR ");
        assertThat(exact.statusCode()).isEqualTo(200);
        assertThat(exact.body()).contains("\"email\":\"roxy@example.com\"").contains("\"name\":\"Ehtisham Badar\"")
                .contains("\"playerId\":\"p1\"").contains("\"dismissed\":false").contains("\"taken\":false");
        String roxy = field(exact.body(), "token");
        assertThat(send("GET", "/api/ledger", null, null, roxy).statusCode()).isEqualTo(200);
        // Nothing to link for it; only the admin sees the list.
        assertThat(send("GET", "/api/account/links", null, null, roxy).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/account/links", null, admin, null).body()).isEqualTo("[]");

        // The same email can't sign up twice; weak passwords and bad emails are refused.
        assertThat(signup("roxy@example.com", "secret123", "Someone").statusCode()).isEqualTo(400);
        assertThat(signup("new@example.com", "123", "Someone").body()).contains("at least 6");
        assertThat(signup("not-an-email", "secret123", "Someone").statusCode()).isEqualTo(400);

        // Only a first name: an account at once, but just the Home tab's share of the ledger (no money, no stats).
        HttpResponse<String> partial = signup("talha@example.com", "secret123", "Talha");
        assertThat(partial.body()).contains("\"playerId\":\"\"").contains("\"possible\":[{\"id\":\"p4\",\"name\":\"Talha Ramzan\"}]");
        String talha = field(partial.body(), "token");
        HttpResponse<String> home = send("GET", "/api/ledger", null, null, talha);
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("\"name\":\"Talha Ramzan\"").contains("\"expenses\":{}").contains("\"stats\":{}").contains("\"charges\":[]")
                .contains("\"payAccount\":null").contains("\"payments\":[]").doesNotContain("\"att\":{\"");
        // The admin sees it in Requests with the player its name suggests, and links it; then it's that player.
        String talhaId = field(partial.body(), "id");
        assertThat(send("GET", "/api/account/links", null, admin, null).body()).contains("\"email\":\"talha@example.com\"").contains("\"suggested\":\"p4\"");
        assertThat(send("PUT", "/api/account/users/" + talhaId + "/player", "{\"playerId\":\"p4\"}", null, talha).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/account/users/" + talhaId + "/player", "{\"playerId\":\"p4\"}", admin, null).body())
                .contains("\"name\":\"Talha Ramzan\"").contains("\"playerId\":\"p4\"");
        assertThat(send("GET", "/api/account/me", null, null, talha).body()).contains("\"playerId\":\"p4\"");
        assertThat(send("GET", "/api/account/links", null, admin, null).body()).doesNotContain("talha@example.com");

        // A first name two players share offers both as possible matches.
        assertThat(signup("nabeel@example.com", "secret123", "nabeel").body())
                .contains("{\"id\":\"p2\",\"name\":\"Nabeel Yousaf\"}").contains("{\"id\":\"p3\",\"name\":\"Nabeel Sohail\"}");

        // A player already linked can't be taken by another account with the same name.
        HttpResponse<String> copy = signup("fake@example.com", "secret123", "Ehtisham Badar");
        assertThat(copy.body()).contains("\"playerId\":\"\"").contains("\"taken\":true").contains("\"possible\":[]");
        // Someone not in the squad: Home only; "Not in the squad" takes them off the Requests list (Home stays).
        HttpResponse<String> stranger = signup("guest@example.com", "secret123", "Imran Khan");
        assertThat(stranger.body()).contains("\"playerId\":\"\"").contains("\"possible\":[]").contains("\"taken\":false");
        String links = send("GET", "/api/account/links", null, admin, null).body();
        assertThat(links).contains("\"email\":\"guest@example.com\"").contains("\"email\":\"fake@example.com\"");
        assertThat(send("POST", "/api/account/users/" + field(stranger.body(), "id") + "/dismiss", null, null, field(stranger.body(), "token")).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/account/users/" + field(stranger.body(), "id") + "/dismiss", null, admin, null).body()).contains("\"dismissed\":true");
        assertThat(send("GET", "/api/account/links", null, admin, null).body()).doesNotContain("guest@example.com");
        assertThat(send("GET", "/api/ledger", null, null, field(stranger.body(), "token")).statusCode()).isEqualTo(200);

        // A guest can make their own profile; bad values are refused; a linked player's squad name stays.
        String guestToken = field(stranger.body(), "token");
        assertThat(send("PUT", "/api/account/profile", "{\"name\":\"Imran K\",\"role\":\"Bowler\",\"battingHand\":\"left\",\"bowlingArm\":\"right\",\"bowlingStyle\":\"Leg spin\",\"about\":\"Weekend player\"}", null, guestToken).body())
                .contains("\"name\":\"Imran K\"").contains("\"profile\":{\"role\":\"Bowler\",\"battingHand\":\"left\",\"bowlingArm\":\"right\",\"bowlingStyle\":\"Leg spin\",\"about\":\"Weekend player\"}");
        assertThat(send("PUT", "/api/account/profile", "{\"role\":\"Captain\"}", null, guestToken).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/account/profile", "{\"role\":\"Bowler\"}", null, null).statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/account/profile", "{\"name\":\"Someone Else\",\"role\":\"\"}", null, roxy).body()).contains("\"name\":\"Ehtisham Badar\"");
        assertThat(send("GET", "/api/account/users", null, admin, null).body()).contains("\"about\":\"Weekend player\"");

        // Players change only their own profile.
        assertThat(send("PUT", "/api/players/p1/profile", "{\"battingHand\":\"left\"}", null, roxy).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/players/p4/profile", "{\"battingHand\":\"left\"}", null, roxy).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/players/p1/profile", "{\"battingHand\":\"left\"}", null, field(stranger.body(), "token")).statusCode()).isEqualTo(403);

        // Signing in: right password works; wrong ones are counted, and five in a row lock the account for a while.
        HttpResponse<String> ok = login("ROXY@example.com", "secret123");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"playerId\":\"p1\"");
        assertThat(login("roxy@example.com", "wrong").statusCode()).isEqualTo(401);
        assertThat(login("nobody@example.com", "secret123").body()).contains("Wrong email or password");
        for (int i = 0; i < 4; i++) assertThat(login("talha@example.com", "nope").body()).contains("Wrong email or password");
        assertThat(login("talha@example.com", "nope").body()).contains("Try again in 15 minutes");
        // Locked: even the right password waits.
        assertThat(login("talha@example.com", "secret123").body()).contains("Too many wrong passwords");

        // Signing out ends that session only.
        String second = field(ok.body(), "token");
        send("POST", "/api/account/logout", null, null, second);
        assertThat(send("GET", "/api/account/me", null, null, second).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/account/me", null, null, roxy).statusCode()).isEqualTo(200);

        // Changing a password needs the current one.
        assertThat(send("PUT", "/api/account/password", "{\"current\":\"bad\",\"next\":\"newpass1\"}", null, roxy).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/account/password", "{\"current\":\"secret123\",\"next\":\"newpass1\"}", null, roxy).statusCode()).isEqualTo(200);
        assertThat(login("roxy@example.com", "newpass1").statusCode()).isEqualTo(200);

        // The admin's list: everyone, with their last sign-in and record (failed tries too). Players can't see it.
        assertThat(send("GET", "/api/account/users", null, null, roxy).statusCode()).isEqualTo(403);
        String users = send("GET", "/api/account/users", null, admin, null).body();
        assertThat(users).contains("\"email\":\"roxy@example.com\"").contains("\"playerName\":\"Ehtisham Badar\"")
                .contains("\"lastLoginMethod\":\"password\"").contains("\"ok\":false").contains("\"email\":\"guest@example.com\"");
        assertThat(send("GET", "/api/account/users/" + field(exact.body(), "id") + "/logins", null, admin, null).body()).contains("\"method\":\"signup\"");

        // The admin can link an account to a player (one account per player) or unlink it.
        String guestId = field(stranger.body(), "id");
        assertThat(send("PUT", "/api/account/users/" + guestId + "/player", "{\"playerId\":\"p1\"}", admin, null).body()).contains("already has an account");
        assertThat(send("PUT", "/api/account/users/" + guestId + "/player", "{\"playerId\":\"p5\"}", admin, null).body()).contains("\"playerId\":\"p5\"").contains("\"name\":\"Haider\"");
        assertThat(send("PUT", "/api/account/users/" + guestId + "/player", "{\"playerId\":\"\"}", admin, null).body()).contains("\"playerId\":\"\"");
    }

    @Test
    void forgotPasswordGoesToTheAdminWhoResetsIt() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", SQUAD, admin, null).statusCode()).isEqualTo(200);
        String id = field(signup("haider@example.com", "secret123", "Haider").body(), "id");

        // Asking twice makes one request; an unknown email says the same and makes none.
        assertThat(send("POST", "/api/account/forgot", "{\"email\":\"Haider@example.com\",\"note\":\"New phone\"}", null, null).statusCode()).isEqualTo(200);
        send("POST", "/api/account/forgot", "{\"email\":\"haider@example.com\"}", null, null);
        assertThat(send("POST", "/api/account/forgot", "{\"email\":\"ghost@example.com\"}", null, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/account/requests", null, null, null).statusCode()).isEqualTo(403);
        String requests = send("GET", "/api/account/requests", null, admin, null).body();
        assertThat(requests.split("\"userId\"", -1).length - 1).isEqualTo(1);
        assertThat(requests).contains("\"note\":\"New phone\"").contains("\"name\":\"Haider\"");

        // The admin resets it: a temporary password, the old one stops working, the request closes.
        String temp = field(send("POST", "/api/account/users/" + id + "/reset", null, admin, null).body(), "tempPassword");
        assertThat(temp).matches("[a-z2-9]{5}-[a-z2-9]{5}");
        assertThat(login("haider@example.com", "secret123").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/account/requests", null, admin, null).body()).isEqualTo("[]");

        // Signing in with it works, but nothing else does until they choose a new password.
        HttpResponse<String> in = login("haider@example.com", temp);
        assertThat(in.body()).contains("\"mustChange\":true");
        String token = field(in.body(), "token");
        assertThat(send("GET", "/api/ledger", null, null, token).statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/account/password", "{\"next\":\"fresh123\"}", null, token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/account/me", null, null, token).body()).contains("\"mustChange\":false");
        assertThat(send("GET", "/api/ledger", null, null, token).statusCode()).isEqualTo(200);
        assertThat(login("haider@example.com", "fresh123").statusCode()).isEqualTo(200);

        // Removing an account signs it out everywhere.
        assertThat(send("DELETE", "/api/account/users/" + id, null, admin, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/account/me", null, null, token).statusCode()).isEqualTo(401);
    }

    @Test
    void biometricSignInRegistersAndSignsIn() throws Exception {
        String admin = field(send("POST", "/api/login", "{\"name\":\"test-key\"}", null, null).body(), "token");
        assertThat(send("PUT", "/api/ledger", SQUAD, admin, null).statusCode()).isEqualTo(200);
        String token = field(signup("shehroz@example.com", "secret123", "Shehroz Khan").body(), "token");

        // The device makes a key pair; the private key never leaves it.
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair device = g.generateKeyPair();
        String credentialId = b64("credential-1".getBytes(StandardCharsets.UTF_8));

        String options = send("POST", "/api/account/passkey/register-options", null, null, token).body();
        assertThat(options).contains("\"rp\":{\"id\":\"localhost\",\"name\":\"Red Falcons\"}").contains("\"userVerification\":\"required\"").contains("\"displayName\":\"Shehroz Khan\"");
        String challenge = field(options, "challenge");
        String reg = "{\"id\":\"" + credentialId + "\",\"clientDataJSON\":\"" + clientData("webauthn.create", challenge, "http://localhost:3000")
                + "\",\"authenticatorData\":\"" + b64(authData(0x05, 0)) + "\",\"publicKey\":\"" + b64(device.getPublic().getEncoded())
                + "\",\"publicKeyAlgorithm\":-7,\"label\":\"Chrome on Mac\"}";
        assertThat(send("POST", "/api/account/passkey/register", reg, null, token).body()).contains("\"label\":\"Chrome on Mac\"");
        // The same challenge can't be used twice.
        assertThat(send("POST", "/api/account/passkey/register", reg, null, token).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/account/me", null, null, token).body()).contains("\"passkeys\":[{\"id\":\"" + credentialId + "\"");

        // Signing in: the device signs the server's challenge after checking the person's biometrics.
        String loginChallenge = field(send("POST", "/api/account/passkey/login-options", null, null, null).body(), "challenge");
        String clientData = clientData("webauthn.get", loginChallenge, "http://localhost:3000");
        byte[] auth = authData(0x05, 1);
        HttpResponse<String> in = send("POST", "/api/account/passkey/login", assertion(credentialId, clientData, auth, sign(device, auth, clientData)), null, null);
        assertThat(in.statusCode()).isEqualTo(200);
        String bioToken = field(in.body(), "token");
        assertThat(send("GET", "/api/ledger", null, null, bioToken).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/account/users", null, admin, null).body()).contains("\"lastLoginMethod\":\"biometric\"");

        // A device that didn't verify the person, another site, or a forged signature are all refused.
        String c2 = field(send("POST", "/api/account/passkey/login-options", null, null, null).body(), "challenge");
        String cd2 = clientData("webauthn.get", c2, "http://localhost:3000");
        byte[] noUv = authData(0x01, 2);
        assertThat(send("POST", "/api/account/passkey/login", assertion(credentialId, cd2, noUv, sign(device, noUv, cd2)), null, null).statusCode()).isEqualTo(401);
        String c3 = field(send("POST", "/api/account/passkey/login-options", null, null, null).body(), "challenge");
        String evil = clientData("webauthn.get", c3, "https://evil.example");
        byte[] a3 = authData(0x05, 3);
        assertThat(send("POST", "/api/account/passkey/login", assertion(credentialId, evil, a3, sign(device, a3, evil)), null, null).statusCode()).isEqualTo(401);
        String c4 = field(send("POST", "/api/account/passkey/login-options", null, null, null).body(), "challenge");
        String cd4 = clientData("webauthn.get", c4, "http://localhost:3000");
        KeyPair other = g.generateKeyPair();
        byte[] a4 = authData(0x05, 4);
        assertThat(send("POST", "/api/account/passkey/login", assertion(credentialId, cd4, a4, sign(other, a4, cd4)), null, null).statusCode()).isEqualTo(401);

        // Turning it off in Settings removes the device; it can't sign in any more.
        assertThat(send("DELETE", "/api/account/passkey/" + credentialId, null, null, token).statusCode()).isEqualTo(200);
        String c5 = field(send("POST", "/api/account/passkey/login-options", null, null, null).body(), "challenge");
        String cd5 = clientData("webauthn.get", c5, "http://localhost:3000");
        byte[] a5 = authData(0x05, 5);
        assertThat(send("POST", "/api/account/passkey/login", assertion(credentialId, cd5, a5, sign(device, a5, cd5)), null, null).body()).contains("isn't on for this device");
    }

    // ---------- a pretend authenticator ----------

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String clientData(String type, String challenge, String origin) {
        return b64(("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}").getBytes(StandardCharsets.UTF_8));
    }

    /** rpIdHash for "localhost", the flags (0x01 user present, 0x04 user verified) and the signature counter. */
    private static byte[] authData(int flags, int counter) throws Exception {
        byte[] rpHash = MessageDigest.getInstance("SHA-256").digest("localhost".getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(37).put(rpHash).put((byte) flags).putInt(counter).array();
    }

    private static String sign(KeyPair key, byte[] authData, String clientDataB64) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key.getPrivate());
        s.update(authData);
        s.update(MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(clientDataB64)));
        return b64(s.sign());
    }

    private static String assertion(String id, String clientData, byte[] auth, String signature) {
        return "{\"id\":\"" + id + "\",\"clientDataJSON\":\"" + clientData + "\",\"authenticatorData\":\"" + b64(auth)
                + "\",\"signature\":\"" + signature + "\",\"remember\":true,\"label\":\"Chrome on Mac\"}";
    }
}

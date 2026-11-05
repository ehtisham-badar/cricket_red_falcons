package com.redfalcons.ledger.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The checks behind biometric sign-in (WebAuthn with the device's own authenticator: Face ID, Touch ID,
 * fingerprint). Each ceremony gets a one-time challenge; the server then checks that the browser answered that
 * challenge, for our site, with the person verified on the device, and (to sign in) signed by the key
 * registered earlier. Registration uses the public key the browser hands over (no attestation is asked for).
 */
@Service
public class WebAuthnService {

    public static final int ES256 = -7;
    public static final int RS256 = -257;
    private static final Duration CHALLENGE_LIFE = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper json;
    private final String rpId;
    private final List<String> origins;
    /** challenge (base64url) → who it was for ('' to sign in) and when it expires. */
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(String userId, String type, Instant expires) {
    }

    /** What the browser sends back after creating a credential (all base64url). */
    public record Registration(String id, String clientDataJSON, String authenticatorData, String publicKey, Integer publicKeyAlgorithm) {
    }

    /** What the browser sends back after signing in with a credential (all base64url). */
    public record Assertion(String id, String clientDataJSON, String authenticatorData, String signature) {
    }

    /** A verified new credential, ready to store. */
    public record NewCredential(String id, byte[] publicKey, int alg, long signCount) {
    }

    public WebAuthnService(ObjectMapper json,
                           @Value("${ledger.webauthn.rp-id:red-falcons-ledger.vercel.app}") String rpId,
                           @Value("${ledger.webauthn.origins:https://red-falcons-ledger.vercel.app}") String origins) {
        this.json = json;
        this.rpId = rpId.trim();
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String rpId() {
        return rpId;
    }

    /** A new one-time challenge: for registering ({@code userId}) or signing in ({@code userId} = ''). */
    public String challenge(String userId, String type) {
        Instant now = Instant.now();
        pending.values().removeIf(p -> p.expires().isBefore(now));
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String c = b64(b);
        pending.put(c, new Pending(userId == null ? "" : userId, type, now.plus(CHALLENGE_LIFE)));
        return c;
    }

    /** Checks a new credential made for {@code userId}; throws IllegalArgumentException when anything is off. */
    public NewCredential verifyRegistration(String userId, Registration r) {
        if (r == null || blank(r.id()) || blank(r.clientDataJSON()) || blank(r.authenticatorData()) || blank(r.publicKey())) {
            throw new IllegalArgumentException("This browser didn't send what's needed for biometric sign-in.");
        }
        checkClientData(unb64(r.clientDataJSON()), "webauthn.create", userId);
        byte[] auth = unb64(r.authenticatorData());
        checkAuthData(auth);
        int alg = r.publicKeyAlgorithm() == null ? ES256 : r.publicKeyAlgorithm();
        byte[] key = unb64(r.publicKey());
        publicKey(key, alg); // throws if it isn't a key we can use
        return new NewCredential(r.id(), key, alg, signCount(auth));
    }

    /** Checks a sign-in with a stored credential; returns the device's new signature counter. */
    public long verifyAssertion(Assertion a, byte[] storedKey, int alg, long storedCount) {
        if (a == null || blank(a.clientDataJSON()) || blank(a.authenticatorData()) || blank(a.signature())) {
            throw new IllegalArgumentException("This browser didn't send a biometric sign-in.");
        }
        byte[] clientData = unb64(a.clientDataJSON());
        checkClientData(clientData, "webauthn.get", "");
        byte[] auth = unb64(a.authenticatorData());
        checkAuthData(auth);
        try {
            Signature sig = Signature.getInstance(alg == RS256 ? "SHA256withRSA" : "SHA256withECDSA");
            sig.initVerify(publicKey(storedKey, alg));
            sig.update(auth);
            sig.update(sha256(clientData));
            if (!sig.verify(unb64(a.signature()))) throw new IllegalArgumentException("Biometric sign-in didn't match this device.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Biometric sign-in didn't match this device.");
        }
        long count = signCount(auth);
        // A counter that goes backwards means a copied key; devices that don't count always send 0.
        if (storedCount > 0 && count <= storedCount) throw new IllegalArgumentException("This biometric sign-in was already used.");
        return count;
    }

    private void checkClientData(byte[] raw, String type, String userId) {
        JsonNode c;
        try {
            c = json.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("The biometric response couldn't be read.");
        }
        if (!type.equals(c.path("type").asString(""))) throw new IllegalArgumentException("Wrong kind of biometric response.");
        String challenge = c.path("challenge").asString("");
        Pending p = pending.remove(challenge);
        if (p == null || p.expires().isBefore(Instant.now()) || !p.type().equals(type) || !p.userId().equals(userId)) {
            throw new IllegalArgumentException("That biometric request expired. Try again.");
        }
        if (!origins.contains(c.path("origin").asString(""))) throw new IllegalArgumentException("That biometric sign-in came from another site.");
    }

    /** rpIdHash (32 bytes), flags, counter (4 bytes): our site, user present and verified (biometrics or device PIN). */
    private void checkAuthData(byte[] auth) {
        if (auth.length < 37) throw new IllegalArgumentException("The biometric response was too short.");
        if (!MessageDigest.isEqual(Arrays.copyOfRange(auth, 0, 32), sha256(rpId.getBytes(StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException("That biometric sign-in is for another site.");
        }
        int flags = auth[32] & 0xFF;
        if ((flags & 0x01) == 0 || (flags & 0x04) == 0) throw new IllegalArgumentException("The device didn't verify it was you.");
    }

    private static long signCount(byte[] auth) {
        return ((auth[33] & 0xFFL) << 24) | ((auth[34] & 0xFFL) << 16) | ((auth[35] & 0xFFL) << 8) | (auth[36] & 0xFFL);
    }

    private static PublicKey publicKey(byte[] spki, int alg) {
        if (alg != ES256 && alg != RS256) throw new IllegalArgumentException("This device's key type isn't supported.");
        try {
            return KeyFactory.getInstance(alg == RS256 ? "RSA" : "EC").generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new IllegalArgumentException("This device's key couldn't be read.");
        }
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] unb64(String s) {
        try {
            return Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The biometric response couldn't be read.");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

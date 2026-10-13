package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.AdminSession;
import com.redfalcons.ledger.repo.AdminSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * The admin "key" is a secret name typed on the start screen; every other name is a read-only viewer.
 * Each admin sign-in gets its own random session token (with two-factor on, only after the code). The
 * server keeps a hash of each token, so a session can be ended (Switch user, or when two-factor is turned
 * on) and a leaked old token stops working.
 */
@Component
public class AdminAuth {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] adminKey;
    private final AdminSessionRepository sessions;

    public AdminAuth(@Value("${ledger.admin-key}") String adminKey, AdminSessionRepository sessions) {
        if (adminKey == null || adminKey.isBlank()) {
            throw new IllegalStateException("No admin key configured. Set the LEDGER_ADMIN_KEY environment variable, "
                    + "or add ledger.admin-key=... to config/application.properties for local runs.");
        }
        this.adminKey = adminKey.trim().getBytes(StandardCharsets.UTF_8);
        this.sessions = sessions;
    }

    public boolean isAdminKey(String name) {
        if (name == null) return false;
        return MessageDigest.isEqual(adminKey, name.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Starts an admin session and returns its token. */
    public String newSession(String label) {
        String token = randomToken();
        sessions.save(new AdminSession(hash(token), clip(label), Instant.now().toString()));
        return token;
    }

    public boolean isValidToken(String candidate) {
        return candidate != null && !candidate.isBlank() && sessions.existsById(hash(candidate));
    }

    public void endSession(String token) {
        if (token != null && !token.isBlank()) sessions.deleteById(hash(token));
    }

    /** Ends every admin session except the given one (used when two-factor is turned on). */
    public void endOtherSessions(String keepToken) {
        String keep = keepToken == null ? "" : hash(keepToken);
        sessions.deleteAll(sessions.findAll().stream().filter(s -> !s.getId().equals(keep)).toList());
    }

    public static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String clip(String label) {
        String t = label == null ? "" : label.trim();
        if (t.isEmpty()) t = "Browser";
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}

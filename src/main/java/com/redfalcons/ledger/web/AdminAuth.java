package com.redfalcons.ledger.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The admin "key" is a secret name typed on the start screen. Anyone who types it gets a
 * token that unlocks writes; every other name is a read-only viewer. The key never leaves
 * the server, and the token is derived from it, so changing the key logs out old admins.
 */
@Component
public class AdminAuth {

    private final byte[] adminKey;
    private final String token;

    public AdminAuth(@Value("${ledger.admin-key}") String adminKey) {
        if (adminKey == null || adminKey.isBlank()) {
            throw new IllegalStateException("No admin key configured. Set the LEDGER_ADMIN_KEY environment variable, "
                    + "or add ledger.admin-key=... to config/application.properties for local runs.");
        }
        this.adminKey = adminKey.trim().getBytes(StandardCharsets.UTF_8);
        this.token = hmac(this.adminKey, "red-falcons-admin");
    }

    public boolean isAdminKey(String name) {
        if (name == null) return false;
        return MessageDigest.isEqual(adminKey, name.trim().getBytes(StandardCharsets.UTF_8));
    }

    public String token() {
        return token;
    }

    public boolean isValidToken(String candidate) {
        if (candidate == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

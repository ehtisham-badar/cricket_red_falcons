package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.TrustedDevice;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TrustedDeviceRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-factor sign-in for the admin with an authenticator app (TOTP: 6 digits, 30 s, SHA-1), one-time
 * recovery codes, and trusted browsers that may skip the code for {@link #TRUST_DAYS} days. Setting
 * LEDGER_RESET_2FA=true and restarting turns two-factor off (for a lost phone and no recovery codes).
 */
@Service
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SECRET = "totpSecret";
    private static final String LAST_STEP = "totpLastStep";
    private static final String RECOVERY = "totpRec.";
    private static final int TRUST_DAYS = 180;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration SETUP_LIFETIME = Duration.ofMinutes(15);
    private static final String ISSUER = "Red Falcons Ledger";

    public record Setup(String secret, String uri) {
    }

    public record Device(String id, String label, String createdAt, String lastUsed, boolean current) {
    }

    private record Pending(String value, Instant expires, int[] attempts) {
    }

    private final SettingRepository settings;
    private final TrustedDeviceRepository devices;
    private final boolean resetOnStart;
    /** challenge id -> expiry/attempts; a challenge is only issued after the correct admin key. */
    private final Map<String, Pending> challenges = new ConcurrentHashMap<>();
    /** session-token hash -> secret being set up. */
    private final Map<String, Pending> setups = new ConcurrentHashMap<>();

    public TwoFactorService(SettingRepository settings, TrustedDeviceRepository devices,
                            @Value("${ledger.reset-2fa:false}") boolean resetOnStart) {
        this.settings = settings;
        this.devices = devices;
        this.resetOnStart = resetOnStart;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetIfAsked() {
        if (resetOnStart && enabled()) {
            clear();
            log.warn("Two-factor sign-in was turned off by LEDGER_RESET_2FA. Unset it and turn two-factor on again.");
        }
    }

    @Transactional(readOnly = true)
    public boolean enabled() {
        return settings.findById(SECRET).map(s -> !s.getValue().isEmpty()).orElse(false);
    }

    // ---------- sign-in ----------

    public String newChallenge() {
        challenges.values().removeIf(p -> p.expires().isBefore(Instant.now()));
        String id = AdminAuth.randomToken();
        challenges.put(id, new Pending("", Instant.now().plus(CHALLENGE_LIFETIME), new int[1]));
        return id;
    }

    /** Checks a code (or recovery code) against a challenge. Returns attempts left, or -1 when accepted. */
    @Transactional
    public int answer(String challenge, String code) {
        Pending p = challenge == null ? null : challenges.get(challenge);
        if (p == null || p.expires().isBefore(Instant.now())) {
            if (challenge != null) challenges.remove(challenge);
            return 0;
        }
        if (verifyCodeOrRecovery(code)) {
            challenges.remove(challenge);
            return -1;
        }
        int left = MAX_ATTEMPTS - (++p.attempts()[0]);
        if (left <= 0) challenges.remove(challenge);
        return Math.max(left, 0);
    }

    // ---------- trusted browsers ----------

    @Transactional
    public String trustDevice(String label) {
        String token = AdminAuth.randomToken();
        Instant now = Instant.now();
        devices.save(new TrustedDevice(AdminAuth.hash(token), label(label), now.toString(), now.plus(Duration.ofDays(TRUST_DAYS)).toString()));
        return token;
    }

    @Transactional
    public boolean isTrusted(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) return false;
        Optional<TrustedDevice> d = devices.findById(AdminAuth.hash(deviceToken));
        if (d.isEmpty()) return false;
        if (d.get().getExpiresAt().compareTo(Instant.now().toString()) < 0) {
            devices.delete(d.get());
            return false;
        }
        d.get().used(Instant.now().toString());
        return true;
    }

    @Transactional(readOnly = true)
    public List<Device> devices(String currentDeviceToken) {
        String current = currentDeviceToken == null || currentDeviceToken.isBlank() ? "" : AdminAuth.hash(currentDeviceToken);
        return devices.findAll().stream()
                .sorted(Comparator.comparing(TrustedDevice::getLastUsed).reversed())
                .map(d -> new Device(d.getId(), d.getLabel(), d.getCreatedAt(), d.getLastUsed(), d.getId().equals(current)))
                .toList();
    }

    @Transactional
    public boolean revokeDevice(String id) {
        if (id == null || !devices.existsById(id)) return false;
        devices.deleteById(id);
        return true;
    }

    // ---------- turning it on and off ----------

    /** Starts setup for this admin session: a new secret to scan, confirmed with {@link #confirm}. */
    public Setup startSetup(String sessionToken) {
        byte[] key = new byte[20];
        RANDOM.nextBytes(key);
        String secret = base32(key);
        setups.put(AdminAuth.hash(sessionToken), new Pending(secret, Instant.now().plus(SETUP_LIFETIME), new int[1]));
        String uri = "otpauth://totp/" + enc(ISSUER + ":Admin") + "?secret=" + secret + "&issuer=" + enc(ISSUER)
                + "&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret, uri);
    }

    /** Turns two-factor on if the code matches the secret being set up; returns the recovery codes, or null. */
    @Transactional
    public List<String> confirm(String sessionToken, String code) {
        Pending p = setups.get(AdminAuth.hash(sessionToken));
        if (p == null || p.expires().isBefore(Instant.now())) return null;
        Long step = matchingStep(base32Decode(p.value()), code, -1);
        if (step == null) return null;
        setups.remove(AdminAuth.hash(sessionToken));
        clear();
        settings.save(new Setting(SECRET, p.value()));
        settings.save(new Setting(LAST_STEP, step.toString()));
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String c = recoveryCode();
            codes.add(c);
            settings.save(new Setting(RECOVERY + recoveryHash(c), "1"));
        }
        return codes;
    }

    /** Turns two-factor off after a valid code or recovery code. */
    @Transactional
    public boolean disable(String code) {
        if (!enabled() || !verifyCodeOrRecovery(code)) return false;
        clear();
        return true;
    }

    @Transactional(readOnly = true)
    public int recoveryLeft() {
        return (int) settings.findAll().stream().filter(s -> s.getKey().startsWith(RECOVERY)).count();
    }

    // ---------- codes ----------

    private boolean verifyCodeOrRecovery(String code) {
        String c = code == null ? "" : code.replaceAll("\\s", "");
        String secret = settings.findById(SECRET).map(Setting::getValue).orElse("");
        if (secret.isEmpty()) return false;
        if (c.matches("\\d{6}")) {
            long last = settings.findById(LAST_STEP).map(s -> Long.parseLong(s.getValue())).orElse(-1L);
            Long step = matchingStep(base32Decode(secret), c, last);
            if (step == null) return false;
            settings.save(new Setting(LAST_STEP, step.toString()));   // the same code can't be used twice
            return true;
        }
        String key = RECOVERY + recoveryHash(c.toLowerCase(Locale.ROOT));
        if (settings.existsById(key)) {
            settings.deleteById(key);   // each recovery code works once
            return true;
        }
        return false;
    }

    /** The time step whose code matches (now, or one step either side), if newer than {@code after}. */
    private static Long matchingStep(byte[] key, String code, long after) {
        if (code == null || !code.trim().matches("\\d{6}")) return null;
        int want = Integer.parseInt(code.trim());
        long now = Instant.now().getEpochSecond() / 30;
        for (long step = now - 1; step <= now + 1; step++) {
            if (step > after && totp(key, step) == want) return step;
        }
        return null;
    }

    static int totp(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            return bin % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void clear() {
        settings.deleteAll(settings.findAll().stream()
                .filter(s -> s.getKey().equals(SECRET) || s.getKey().equals(LAST_STEP) || s.getKey().startsWith(RECOVERY)).toList());
        devices.deleteAllInBatch();
    }

    private static String recoveryCode() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) b.append('-');
            b.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return b.toString();
    }

    private static String recoveryHash(String code) {
        return AdminAuth.hash("rf-recovery:" + code).substring(0, 40);
    }

    private static String label(String l) {
        String t = l == null ? "" : l.trim();
        if (t.isEmpty()) t = "Browser";
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(B32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    static byte[] base32Decode(String s) {
        ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8 + 1);
        int buffer = 0, bits = 0;
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            int v = B32.indexOf(c);
            if (v < 0) continue;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return Arrays.copyOf(out.array(), out.position());
    }
}

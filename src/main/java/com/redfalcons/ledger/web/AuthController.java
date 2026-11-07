package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.TwoFactorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signing in and out, and the admin's two-factor settings. With two-factor on, the admin key alone only
 * earns a short-lived challenge; the session token comes after a valid code, unless this browser is trusted.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    static final String DEVICE_HEADER = "X-Device";

    private final AdminAuth auth;
    private final TwoFactorService twoFactor;

    public AuthController(AdminAuth auth, TwoFactorService twoFactor) {
        this.auth = auth;
        this.twoFactor = twoFactor;
    }

    public record LoginRequest(String name, String device, String label) {
    }

    public record VerifyRequest(String challenge, String code, Boolean trust, String label) {
    }

    public record CodeRequest(String code, String label) {
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String name = req == null || req.name() == null ? "" : req.name().trim();
        Map<String, Object> out = new HashMap<>();
        if (!auth.isAdminKey(name)) {
            out.put("name", name.isEmpty() ? "Guest" : name);
            out.put("admin", false);
            out.put("token", null);
            return out;
        }
        out.put("name", "Admin");
        if (!twoFactor.enabled() || twoFactor.isTrusted(req.device())) {
            out.put("admin", true);
            out.put("token", auth.newSession(req.label()));
            return out;
        }
        out.put("admin", false);
        out.put("token", null);
        out.put("needCode", true);
        out.put("challenge", twoFactor.newChallenge());
        return out;
    }

    @PostMapping("/login/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "Enter the code."));
        int left = twoFactor.answer(req.challenge(), req.code());
        if (left >= 0) {
            String msg = left == 0 ? "Too many tries or the sign-in expired. Enter the admin key again."
                    : "That code didn’t work. " + left + " tr" + (left == 1 ? "y" : "ies") + " left.";
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg, "attemptsLeft", left));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("name", "Admin");
        out.put("admin", true);
        out.put("token", auth.newSession(req.label()));
        out.put("device", Boolean.FALSE.equals(req.trust()) ? null : twoFactor.trustDevice(req.label()));
        out.put("recoveryLeft", twoFactor.recoveryLeft());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        auth.endSession(token);
        return Map.of("ok", true);
    }

    @GetMapping("/session")
    public Map<String, Boolean> session(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        return Map.of("admin", auth.isValidToken(token));
    }

    // ---------- two-factor settings (admin) ----------

    @GetMapping("/2fa")
    public ResponseEntity<?> status(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @RequestHeader(value = DEVICE_HEADER, required = false) String device) {
        if (!auth.isValidToken(token)) return forbidden();
        boolean on = twoFactor.enabled();
        return ResponseEntity.ok(Map.of("enabled", on, "recoveryLeft", on ? twoFactor.recoveryLeft() : 0,
                "devices", on ? twoFactor.devices(device) : List.of()));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setup(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return forbidden();
        return ResponseEntity.ok(twoFactor.startSetup(token));
    }

    /** Turns two-factor on: other admin sessions end, and this browser becomes trusted. */
    @PostMapping("/2fa/confirm")
    public ResponseEntity<?> confirm(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                     @RequestBody CodeRequest req) {
        if (!auth.isValidToken(token)) return forbidden();
        List<String> codes = twoFactor.confirm(token, req == null ? null : req.code());
        if (codes == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "That code didn’t match. Check the time on your phone and try the newest code."));
        }
        auth.endOtherSessions(token);
        return ResponseEntity.ok(Map.of("recoveryCodes", codes, "device", twoFactor.trustDevice(req.label())));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disable(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                     @RequestBody CodeRequest req) {
        if (!auth.isValidToken(token)) return forbidden();
        if (!twoFactor.disable(req == null ? null : req.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "That code didn’t work."));
        }
        return ResponseEntity.ok(Map.of("enabled", false));
    }

    @DeleteMapping("/2fa/devices/{id}")
    public ResponseEntity<?> revoke(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @PathVariable String id) {
        if (!auth.isValidToken(token)) return forbidden();
        return twoFactor.revokeDevice(id) ? ResponseEntity.ok(Map.of("revoked", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such browser."));
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can do this."));
    }
}

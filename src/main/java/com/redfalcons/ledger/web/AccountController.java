package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.LoginEvent;
import com.redfalcons.ledger.model.Passkey;
import com.redfalcons.ledger.model.UserAccount;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.service.AccountService;
import com.redfalcons.ledger.service.ManagerAccessService;
import com.redfalcons.ledger.service.NameMatch;
import com.redfalcons.ledger.service.WebAuthnService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Player accounts: sign up, sign in (password or biometrics), password changes and "forgot password". The
 * admin's side: new accounts to link to a squad player, the list of accounts with their sign-in record,
 * password resets and removing accounts.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;
    private final WebAuthnService webAuthn;
    private final PlayerRepository players;
    private final AdminAuth auth;
    private final ManagerAccessService managers;

    public AccountController(AccountService accounts, WebAuthnService webAuthn, PlayerRepository players, AdminAuth auth,
                             ManagerAccessService managers) {
        this.accounts = accounts;
        this.webAuthn = webAuthn;
        this.players = players;
        this.auth = auth;
        this.managers = managers;
    }

    public record SignupBody(String email, String password, String name, Boolean remember, String label) {
    }

    public record LoginBody(String email, String password, Boolean remember, String label) {
    }

    public record PasswordBody(String current, String next) {
    }

    public record ForgotBody(String email, String note) {
    }

    public record PasskeyBody(String id, String clientDataJSON, String authenticatorData, String publicKey,
                              Integer publicKeyAlgorithm, String signature, Boolean remember, String label) {
    }

    public record PlayerBody(String playerId) {
    }

    public record RoleBody(String role) {
    }

    // ---------- the player ----------

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupBody b) {
        return run(() -> {
            AccountService.SignedUp s = accounts.signup(b.email(), b.password(), b.name(), Boolean.TRUE.equals(b.remember()), b.label());
            Map<String, Object> out = me(s.account());
            out.put("token", s.token());
            out.put("possible", s.possible().stream().map(AccountController::squad).toList());
            out.put("taken", s.taken());
            return out;
        });
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginBody b) {
        return run(() -> {
            AccountService.Signed s = accounts.login(b.email(), b.password(), Boolean.TRUE.equals(b.remember()), b.label());
            Map<String, Object> out = me(s.account());
            out.put("token", s.token());
            staffTokens(out, s.account(), b.label());
            return out;
        }, HttpStatus.UNAUTHORIZED);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = AccountFilter.USER_HEADER, required = false) String token) {
        accounts.logout(token);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public ResponseEntity<?> who(HttpServletRequest req) {
        UserAccount a = account(req);
        if (a == null) return signIn();
        Map<String, Object> out = me(a);
        out.put("possible", a.isMustChange() ? List.of() : accounts.possibleFor(a).stream().map(AccountController::squad).toList());
        out.put("passkeys", accounts.passkeysOf(a).stream().map(AccountController::passkey).toList());
        return ResponseEntity.ok(out);
    }

    @PutMapping("/password")
    public ResponseEntity<?> password(HttpServletRequest req, @RequestBody PasswordBody b) {
        UserAccount a = account(req);
        if (a == null) return signIn();
        return run(() -> {
            accounts.changePassword(a, b == null ? null : b.current(), b == null ? null : b.next());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            // An admin or manager who had to choose a new password gets their staff session now.
            staffTokens(out, a, null);
            return out;
        });
    }

    @PostMapping("/forgot")
    public Map<String, Object> forgot(@RequestBody ForgotBody b) {
        accounts.forgot(b == null ? null : b.email(), b == null ? null : b.note());
        return Map.of("ok", true);
    }

    // ---------- biometrics (WebAuthn) ----------

    @PostMapping("/passkey/register-options")
    public ResponseEntity<?> registerOptions(HttpServletRequest req) {
        UserAccount a = account(req);
        if (a == null) return signIn();
        if (a.isMustChange()) return mustChange();
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("challenge", webAuthn.challenge(a.getId(), "webauthn.create"));
        o.put("rp", obj("id", webAuthn.rpId(), "name", "Red Falcons"));
        o.put("user", obj("id", Base64.getUrlEncoder().withoutPadding().encodeToString(a.getId().getBytes(StandardCharsets.UTF_8)),
                "name", a.getEmail(), "displayName", a.getName()));
        o.put("pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", WebAuthnService.ES256), Map.of("type", "public-key", "alg", WebAuthnService.RS256)));
        o.put("authenticatorSelection", Map.of("authenticatorAttachment", "platform", "userVerification", "required", "residentKey", "preferred"));
        o.put("attestation", "none");
        o.put("timeout", 60000);
        o.put("excludeCredentials", accounts.passkeysOf(a).stream().map(p -> Map.of("type", "public-key", "id", p.getId())).toList());
        return ResponseEntity.ok(o);
    }

    @PostMapping("/passkey/register")
    public ResponseEntity<?> register(HttpServletRequest req, @RequestBody PasskeyBody b) {
        UserAccount a = account(req);
        if (a == null) return signIn();
        if (a.isMustChange()) return mustChange();
        return run(() -> passkey(accounts.addPasskey(a, new WebAuthnService.Registration(b.id(), b.clientDataJSON(),
                b.authenticatorData(), b.publicKey(), b.publicKeyAlgorithm()), b.label())));
    }

    @DeleteMapping("/passkey/{id}")
    public ResponseEntity<?> removePasskey(HttpServletRequest req, @PathVariable String id) {
        UserAccount a = account(req);
        if (a == null) return signIn();
        return accounts.removePasskey(a, id) ? ResponseEntity.ok(Map.of("removed", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Biometric sign-in wasn't on for that device."));
    }

    @PostMapping("/passkey/login-options")
    public Map<String, Object> loginOptions() {
        return Map.of("challenge", webAuthn.challenge("", "webauthn.get"), "rpId", webAuthn.rpId(), "timeout", 60000, "userVerification", "required");
    }

    @PostMapping("/passkey/login")
    public ResponseEntity<?> passkeyLogin(@RequestBody PasskeyBody b) {
        return run(() -> {
            AccountService.Signed s = accounts.loginWithPasskey(new WebAuthnService.Assertion(b.id(), b.clientDataJSON(), b.authenticatorData(), b.signature()),
                    !Boolean.FALSE.equals(b.remember()), b.label());
            Map<String, Object> out = me(s.account());
            out.put("token", s.token());
            staffTokens(out, s.account(), b.label());
            return out;
        }, HttpStatus.UNAUTHORIZED);
    }

    // ---------- the admin ----------

    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return adminOnly();
        var open = accounts.openRequests();
        return ResponseEntity.ok(accounts.all().stream().map(a -> {
            Map<String, Object> m = me(a);
            List<LoginEvent> ev = accounts.eventsOf(a.getId());
            m.put("lastLoginAt", a.getLastLoginAt());
            m.put("lastLoginMethod", a.getLastLoginMethod());
            m.put("loginCount", a.getLoginCount());
            m.put("failedSinceLogin", a.getFailedCount());
            m.put("passkeys", accounts.passkeyCount(a.getId()));
            m.put("resetRequested", open.stream().anyMatch(r -> r.getUserId().equals(a.getId())));
            m.put("recent", ev.stream().limit(10).map(AccountController::event).toList());
            return m;
        }).toList());
    }

    @GetMapping("/users/{id}/logins")
    public ResponseEntity<?> logins(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token, @PathVariable String id) {
        if (!auth.isValidToken(token)) return adminOnly();
        return ResponseEntity.ok(accounts.eventsOf(id).stream().map(AccountController::event).toList());
    }

    /** A temporary password for the admin to pass on; the player sets a new one at their next sign-in. */
    @PostMapping("/users/{id}/reset")
    public ResponseEntity<?> reset(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token, @PathVariable String id) {
        if (!auth.isValidToken(token)) return adminOnly();
        endStaff(id);
        return run(() -> Map.of("tempPassword", accounts.resetPassword(id)));
    }

    @PutMapping("/users/{id}/player")
    public ResponseEntity<?> setPlayer(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                       @PathVariable String id, @RequestBody PlayerBody b) {
        if (!auth.isValidToken(token)) return adminOnly();
        return run(() -> me(accounts.setPlayer(id, b == null ? null : b.playerId())));
    }

    /** Gives an account the manager or admin role, or takes it away (role ""). Only the admin can; their staff sessions end. */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> setRole(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                     @PathVariable String id, @RequestBody RoleBody b) {
        if (!auth.isValidToken(token)) return adminOnly();
        return run(() -> {
            UserAccount a = accounts.setRole(id, b == null ? null : b.role());
            // The new rights start at the account's next sign-in; any old staff session ends now.
            endStaff(a.getId());
            return me(a);
        });
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token, @PathVariable String id) {
        if (!auth.isValidToken(token)) return adminOnly();
        endStaff(id);
        return accounts.delete(id) ? ResponseEntity.ok(Map.of("deleted", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such account."));
    }

    /** Open "forgot password" requests, for the admin's requests box. */
    @GetMapping("/requests")
    public ResponseEntity<?> requests(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return adminOnly();
        return ResponseEntity.ok(accounts.openRequests().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("note", r.getNote());
            m.put("createdAt", r.getCreatedAt());
            accounts.find(r.getUserId()).ifPresent(a -> { m.put("name", a.getName()); m.put("email", a.getEmail()); m.put("playerId", a.getPlayerId()); });
            return m;
        }).toList());
    }

    /** New accounts not linked to a squad player yet, for the admin's Requests, each with a suggested player. */
    @GetMapping("/links")
    public ResponseEntity<?> links(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return adminOnly();
        return ResponseEntity.ok(accounts.unlinked().stream().map(a -> {
            Map<String, Object> m = me(a);
            m.put("suggested", accounts.suggestionFor(a));
            return m;
        }).toList());
    }

    /** "Not in the squad": the account stays on Home only and leaves the Requests list. */
    @PostMapping("/users/{id}/dismiss")
    public ResponseEntity<?> dismiss(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token, @PathVariable String id) {
        if (!auth.isValidToken(token)) return adminOnly();
        return run(() -> me(accounts.dismiss(id)));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<?> decline(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token, @PathVariable String id) {
        if (!auth.isValidToken(token)) return adminOnly();
        accounts.declineRequest(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- helpers ----------

    private static UserAccount account(HttpServletRequest req) {
        return req.getAttribute(AccountFilter.ACCOUNT) instanceof UserAccount a ? a : null;
    }

    private Map<String, Object> me(UserAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("email", a.getEmail());
        m.put("name", a.getName());
        String pid = a.getPlayerId();
        var p = pid.isEmpty() ? java.util.Optional.<com.redfalcons.ledger.model.Player>empty() : players.findById(pid);
        m.put("playerId", p.isPresent() ? pid : "");
        m.put("playerName", p.map(x -> x.getName()).orElse(""));
        m.put("dismissed", a.isDismissed());
        m.put("mustChange", a.isMustChange());
        m.put("role", a.getRole());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    /** An admin or manager account also gets a staff session (never before it has chosen its own password). */
    private void staffTokens(Map<String, Object> out, UserAccount a, String label) {
        if (a.isMustChange()) return;
        if ("admin".equals(a.getRole())) out.put("adminToken", auth.newSession(label, a.getId()));
        else if (ManagerAccessService.ROLE.equals(a.getRole())) out.put("managerToken", managers.startSession(a.getId()));
    }

    private void endStaff(String accountId) {
        auth.endAccountSessions(accountId);
        managers.dropAccount(accountId);
    }

    /** Ordered maps, so the JSON always reads the same way. */
    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> squad(NameMatch.Squad s) {
        return obj("id", s.id(), "name", s.name());
    }

    private static Map<String, Object> passkey(Passkey p) {
        return obj("id", p.getId(), "label", p.getLabel(), "createdAt", p.getCreatedAt(), "lastUsedAt", p.getLastUsedAt());
    }

    private static Map<String, Object> event(LoginEvent e) {
        return obj("at", e.getAt(), "method", e.getMethod(), "label", e.getLabel(), "ok", e.isOk());
    }

    private static ResponseEntity<?> run(Supplier<Object> action) {
        return run(action, HttpStatus.BAD_REQUEST);
    }

    private static ResponseEntity<?> run(Supplier<Object> action, HttpStatus onError) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(onError).body(Map.of("error", e.getMessage()));
        }
    }

    private static ResponseEntity<?> signIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in again.", "signIn", true));
    }

    private static ResponseEntity<?> mustChange() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Set a new password first.", "mustChange", true));
    }

    private static ResponseEntity<?> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can do this."));
    }
}

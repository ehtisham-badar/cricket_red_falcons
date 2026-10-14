package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.ManagerAccess;
import com.redfalcons.ledger.service.LedgerService;
import com.redfalcons.ledger.service.ManagerAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * App managers. A viewer requests access; the admin approves, declines or revokes it. An approved manager
 * can set the playing XI (and 12th man) of matches that aren't finished, and nothing else.
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    static final String MANAGER_HEADER = "X-Manager-Token";

    private final ManagerAccessService access;
    private final LedgerService ledger;
    private final AdminAuth auth;

    public ManagerController(ManagerAccessService access, LedgerService ledger, AdminAuth auth) {
        this.access = access;
        this.ledger = ledger;
        this.auth = auth;
    }

    static final String REQUEST_HEADER = "X-Request-Token";

    public record AccessRequest(String name, String phrase, String label) {
    }

    public record PhraseBody(String phrase) {
    }

    public record XiBody(Map<String, List<String>> playing, Map<String, String> twelfth) {
    }

    // ---------- the requester ----------

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody(required = false) AccessRequest req) {
        try {
            String secret = access.request(req == null ? null : req.name(), req == null ? null : req.phrase(),
                    req == null ? null : req.label());
            return ResponseEntity.ok(Map.of("token", secret, "status", ManagerAccess.PENDING));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Whether the caller's request is "pending", "approved" or "none". */
    @GetMapping("/status")
    public Map<String, String> status(@RequestHeader(value = REQUEST_HEADER, required = false) String secret) {
        return Map.of("status", access.status(secret));
    }

    /** Withdraws the caller's own request, or gives up their own access. */
    @DeleteMapping("/request")
    public Map<String, Boolean> leave(@RequestHeader(value = REQUEST_HEADER, required = false) String secret) {
        access.leave(secret);
        return Map.of("ok", true);
    }

    // ---------- a signed-in manager ----------

    @GetMapping("/session")
    public Map<String, Object> session(@RequestHeader(value = MANAGER_HEADER, required = false) String token) {
        return Map.of("manager", access.isManager(token));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = MANAGER_HEADER, required = false) String token) {
        access.logout(token);
        return Map.of("ok", true);
    }

    /** Sets the XI and 12th man for the matches sent. The admin may do this too. */
    @PutMapping("/xi")
    public ResponseEntity<?> xi(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                @RequestHeader(value = MANAGER_HEADER, required = false) String token,
                                @RequestBody XiBody body) {
        boolean admin = auth.isValidToken(adminToken);
        if (!admin && !access.isManager(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Manager access is no longer valid."));
        }
        try {
            ledger.saveXi(body == null ? null : body.playing(), body == null ? null : body.twelfth(), admin);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- the admin ----------

    public record Row(String id, String name, String label, String phrase, String status, String createdAt, String decidedAt) {
    }

    @GetMapping("/requests")
    public ResponseEntity<?> list(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return forbidden();
        return ResponseEntity.ok(access.list().stream()
                .map(m -> new Row(m.getId(), m.getName(), m.getLabel(), m.getPhrase(), m.getStatus(), m.getCreatedAt(), m.getDecidedAt()))
                .toList());
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<?> approve(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                     @PathVariable String id) {
        if (!auth.isValidToken(token)) return forbidden();
        return access.approve(id) ? ResponseEntity.ok(Map.of("approved", id)) : notFound();
    }

    /** Changes a manager's phrase; their signed-in browsers are signed out. */
    @PutMapping("/requests/{id}/phrase")
    public ResponseEntity<?> phrase(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @PathVariable String id, @RequestBody PhraseBody body) {
        if (!auth.isValidToken(token)) return forbidden();
        try {
            return access.changePhrase(id, body == null ? null : body.phrase())
                    ? ResponseEntity.ok(Map.of("changed", id)) : notFound();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Declines a pending request, or revokes approved access. */
    @DeleteMapping("/requests/{id}")
    public ResponseEntity<?> remove(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @PathVariable String id) {
        if (!auth.isValidToken(token)) return forbidden();
        return access.remove(id) ? ResponseEntity.ok(Map.of("removed", id)) : notFound();
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can do this."));
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such request."));
    }
}

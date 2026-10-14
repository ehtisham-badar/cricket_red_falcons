package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.ManagerAccess;
import com.redfalcons.ledger.model.LineupRequest;
import com.redfalcons.ledger.service.LineupService;
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
    private final LineupService lineups;
    private final AdminAuth auth;

    public ManagerController(ManagerAccessService access, LineupService lineups, AdminAuth auth) {
        this.access = access;
        this.lineups = lineups;
        this.auth = auth;
    }

    static final String REQUEST_HEADER = "X-Request-Token";

    public record AccessRequest(String name, String phrase, String label) {
    }

    public record PhraseBody(String phrase) {
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

    /** Whether the caller's request is "pending", "approved", "declined" (with the admin's note) or "none". */
    @GetMapping("/status")
    public Map<String, String> status(@RequestHeader(value = REQUEST_HEADER, required = false) String secret) {
        return access.byRequest(secret)
                .map(m -> Map.of("status", m.getStatus(), "note", m.getNote() == null ? "" : m.getNote()))
                .orElse(Map.of("status", ManagerAccessService.NONE, "note", ""));
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

    public record LineupBody(String matchId, List<String> playing, String twelfth) {
    }

    public record LineupRow(String id, String managerName, String matchId, List<String> playing, String twelfth,
                            String status, String note, String createdAt, String decidedAt) {
        static LineupRow of(LineupRequest r) {
            return new LineupRow(r.getId(), r.getManagerName(), r.getMatchId(),
                    r.getPlaying().isEmpty() ? List.of() : List.of(r.getPlaying().split(",")), r.getTwelfth(),
                    r.getStatus(), r.getNote() == null ? "" : r.getNote(), r.getCreatedAt(), r.getDecidedAt());
        }
    }

    /** A manager proposes the XI and 12th man for a match; the admin has to approve it. */
    @PostMapping("/lineups")
    public ResponseEntity<?> submit(@RequestHeader(value = MANAGER_HEADER, required = false) String token,
                                    @RequestBody LineupBody body) {
        var manager = access.find(token);
        if (manager.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Manager access is no longer valid."));
        }
        try {
            return ResponseEntity.ok(LineupRow.of(lineups.submit(manager.get(), body == null ? null : body.matchId(),
                    body == null ? null : body.playing(), body == null ? null : body.twelfth())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** A manager's own recent lineup requests and what became of them. */
    @GetMapping("/lineups")
    public ResponseEntity<?> mine(@RequestHeader(value = MANAGER_HEADER, required = false) String token) {
        var manager = access.find(token);
        if (manager.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Manager access is no longer valid."));
        }
        return ResponseEntity.ok(lineups.mine(manager.get().getId()).stream().map(LineupRow::of).toList());
    }

    // ---------- the admin ----------

    public record Row(String id, String name, String label, String phrase, String status, String createdAt, String decidedAt) {
    }

    /** Everything waiting for the admin, plus the approved managers. */
    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return forbidden();
        return ResponseEntity.ok(Map.of(
                "access", access.list().stream().filter(m -> !ManagerAccess.DECLINED.equals(m.getStatus()))
                        .map(m -> new Row(m.getId(), m.getName(), m.getLabel(), m.getPhrase(), m.getStatus(), m.getCreatedAt(), m.getDecidedAt()))
                        .toList(),
                "lineups", lineups.pending().stream().map(LineupRow::of).toList()));
    }

    public record DeclineBody(String note) {
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<?> declineAccess(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                           @PathVariable String id, @RequestBody(required = false) DeclineBody body) {
        if (!auth.isValidToken(token)) return forbidden();
        return access.decline(id, body == null ? null : body.note()) ? ResponseEntity.ok(Map.of("declined", id)) : notFound();
    }

    @PostMapping("/lineups/{id}/approve")
    public ResponseEntity<?> approveLineup(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                           @PathVariable String id) {
        if (!auth.isValidToken(token)) return forbidden();
        try {
            return lineups.approve(id) ? ResponseEntity.ok(Map.of("approved", id)) : notFound();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/lineups/{id}/decline")
    public ResponseEntity<?> declineLineup(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                           @PathVariable String id, @RequestBody(required = false) DeclineBody body) {
        if (!auth.isValidToken(token)) return forbidden();
        return lineups.decline(id, body == null ? null : body.note()) ? ResponseEntity.ok(Map.of("declined", id)) : notFound();
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

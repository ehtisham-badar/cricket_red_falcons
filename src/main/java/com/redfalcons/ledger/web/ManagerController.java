package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.LineupRequest;
import com.redfalcons.ledger.service.LineupService;
import com.redfalcons.ledger.service.ManagerAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * App managers: accounts the admin gave the manager role. A manager proposes the playing XI (and 12th man) of
 * matches that aren't finished, and the admin approves it.
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

    // ---------- a signed-in manager ----------

    @GetMapping("/session")
    public Map<String, Object> session(@RequestHeader(value = MANAGER_HEADER, required = false) String token) {
        return Map.of("manager", access.isManager(token), "id", access.find(token).map(m -> m.getId()).orElse(""));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = MANAGER_HEADER, required = false) String token) {
        access.logout(token);
        return Map.of("ok", true);
    }

    public record LineupBody(String matchId, List<String> playing, String twelfth, List<String> batting) {
    }

    public record LineupRow(String id, String managerName, String matchId, List<String> playing, String twelfth,
                            List<String> batting, String status, String note, String createdAt, String decidedAt) {
        static LineupRow of(LineupRequest r) {
            return new LineupRow(r.getId(), r.getManagerName(), r.getMatchId(),
                    r.getPlaying().isEmpty() ? List.of() : List.of(r.getPlaying().split(",")), r.getTwelfth(),
                    r.getBatting().isEmpty() ? List.of() : List.of(r.getBatting().split(",")), r.getStatus(), r.getNote() == null ? "" : r.getNote(), r.getCreatedAt(), r.getDecidedAt());
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
                    body == null ? null : body.playing(), body == null ? null : body.twelfth(),
                    body == null ? null : body.batting())));
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

    /** The lineups managers proposed, waiting for the admin. */
    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return forbidden();
        return ResponseEntity.ok(Map.of("lineups", lineups.pending().stream().map(LineupRow::of).toList()));
    }

    public record DeclineBody(String note) {
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

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can do this."));
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such request."));
    }
}

package com.redfalcons.ledger.web;

import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.service.EngageService;
import com.redfalcons.ledger.service.EngageService.Who;
import com.redfalcons.ledger.service.ManagerAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Man of the match votes, polls, predictions and the captain's notes (see {@link EngageService}). */
@RestController
@RequestMapping("/api/engage")
public class EngageController {

    private final EngageService engage;
    private final ManagerAccessService managers;
    private final PlayerRepository players;
    private final AdminAuth auth;

    public EngageController(EngageService engage, ManagerAccessService managers, PlayerRepository players, AdminAuth auth) {
        this.engage = engage;
        this.managers = managers;
        this.players = players;
        this.auth = auth;
    }

    private Who who(String adminToken, String managerToken, String viewer) {
        boolean admin = auth.isValidToken(adminToken);
        var mgr = admin ? java.util.Optional.<com.redfalcons.ledger.model.ManagerAccess>empty() : managers.find(managerToken);
        var player = viewer == null || viewer.isBlank() ? java.util.Optional.<com.redfalcons.ledger.model.Player>empty() : players.findById(viewer);
        return new Who(admin, mgr.map(m -> m.getId()).orElse(null), mgr.map(m -> m.getName()).orElse(null),
                player.map(p -> p.getId()).orElse(null), player.map(p -> p.getName()).orElse(null));
    }

    private static ResponseEntity<?> run(Supplier<Object> action) {
        try {
            Object out = action.get();
            return ResponseEntity.ok(out == null ? Map.of("ok", true) : out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
    }

    private static final String T = LedgerController.TOKEN_HEADER;
    private static final String M = ManagerController.MANAGER_HEADER;
    private static final String V = LedgerController.VIEWER_HEADER;

    @GetMapping
    public EngageService.Engage view(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                     @RequestHeader(value = V, required = false) String v) {
        return engage.view(who(a, m, v));
    }

    public record Pick(String candidateId) {
    }

    @PutMapping("/mom/{matchId}")
    public ResponseEntity<?> vote(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                  @RequestHeader(value = V, required = false) String v, @PathVariable String matchId, @RequestBody Pick body) {
        return run(() -> { engage.vote(who(a, m, v), matchId, body == null ? null : body.candidateId()); return null; });
    }

    @PostMapping("/mom/{matchId}/close")
    public ResponseEntity<?> closeMom(@RequestHeader(value = T, required = false) String a, @PathVariable String matchId) {
        if (!auth.isValidToken(a)) return forbidden("Only the admin can close the voting.");
        return run(() -> { engage.setMomClosed(matchId, true); return null; });
    }

    @PostMapping("/mom/{matchId}/reopen")
    public ResponseEntity<?> reopenMom(@RequestHeader(value = T, required = false) String a, @PathVariable String matchId) {
        if (!auth.isValidToken(a)) return forbidden("Only the admin can reopen the voting.");
        return run(() -> { engage.setMomClosed(matchId, false); return null; });
    }

    public record NewPoll(String question, List<String> options, String matchId) {
    }

    @PostMapping("/polls")
    public ResponseEntity<?> createPoll(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                        @RequestBody NewPoll body) {
        Who w = who(a, m, null);
        if (!w.staff()) return forbidden("Only the admin and managers can ask a poll.");
        return run(() -> { engage.createPoll(w, body == null ? null : body.question(), body == null ? null : body.options(), body == null ? null : body.matchId()); return null; });
    }

    public record Answer(Integer option) {
    }

    @PutMapping("/polls/{id}/vote")
    public ResponseEntity<?> answer(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                    @RequestHeader(value = V, required = false) String v, @PathVariable String id, @RequestBody Answer body) {
        return run(() -> { engage.answerPoll(who(a, m, v), id, body == null || body.option() == null ? -1 : body.option()); return null; });
    }

    @PostMapping("/polls/{id}/toggle")
    public ResponseEntity<?> toggle(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m, @PathVariable String id) {
        return engage.managePoll(who(a, m, null), id, false) ? ResponseEntity.ok(Map.of("ok", true)) : forbidden("That poll isn't yours to change.");
    }

    @DeleteMapping("/polls/{id}")
    public ResponseEntity<?> deletePoll(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m, @PathVariable String id) {
        return engage.managePoll(who(a, m, null), id, true) ? ResponseEntity.ok(Map.of("ok", true)) : forbidden("That poll isn't yours to delete.");
    }

    public record Guess(String pick) {
    }

    @PutMapping("/predict/{matchId}")
    public ResponseEntity<?> predict(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                     @RequestHeader(value = V, required = false) String v, @PathVariable String matchId, @RequestBody Guess body) {
        return run(() -> { engage.predict(who(a, m, v), matchId, body == null ? null : body.pick()); return null; });
    }

    @GetMapping("/notes/{matchId}")
    public ResponseEntity<?> notes(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m, @PathVariable String matchId) {
        if (!who(a, m, null).staff()) return forbidden("Only the admin and managers can read the captain's notes.");
        return ResponseEntity.ok(engage.notes(matchId));
    }

    public record NotesBody(String fieldPlan, String battingPlan, String opposition) {
    }

    @PutMapping("/notes/{matchId}")
    public ResponseEntity<?> saveNotes(@RequestHeader(value = T, required = false) String a, @RequestHeader(value = M, required = false) String m,
                                       @PathVariable String matchId, @RequestBody NotesBody body) {
        Who w = who(a, m, null);
        if (!w.staff()) return forbidden("Only the admin and managers can write the captain's notes.");
        return run(() -> engage.saveNotes(w, matchId, body == null ? null : body.fieldPlan(), body == null ? null : body.battingPlan(),
                body == null ? null : body.opposition()));
    }
}

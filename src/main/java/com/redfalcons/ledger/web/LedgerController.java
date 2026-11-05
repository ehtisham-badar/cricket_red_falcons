package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.LedgerService;
import com.redfalcons.ledger.service.ManagerAccessService;
import com.redfalcons.ledger.service.SeasonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LedgerController {

    static final String TOKEN_HEADER = "X-Admin-Token";
    /** The player id a viewer says they are; with viewer access "own", only their payments are sent. */
    static final String VIEWER_HEADER = "X-Viewer";

    private final LedgerService service;
    private final SeasonService seasons;
    private final AdminAuth auth;
    private final ManagerAccessService managers;

    public LedgerController(LedgerService service, SeasonService seasons, AdminAuth auth, ManagerAccessService managers) {
        this.service = service;
        this.seasons = seasons;
        this.auth = auth;
        this.managers = managers;
    }

    public record ArchiveRequest(String name) {
    }

    private static final ResponseEntity<?> FORBIDDEN = ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "View-only access. Only the admin can make changes."));

    /**
     * The ledger is for the team: the admin, a manager, or a signed-in player whose account is linked to a squad
     * player (X-Viewer is then set from their sign-in by {@link AccountFilter}). Everyone else is asked to sign in.
     */
    private boolean canRead(String token, String manager, String viewer) {
        return auth.isValidToken(token) || (viewer != null && !viewer.isBlank()) || managers.find(manager).isPresent();
    }

    private static final ResponseEntity<?> SIGN_IN = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Sign in to see the team's ledger.", "signIn", true));

    @GetMapping("/seasons")
    public ResponseEntity<?> listSeasons(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                         @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String manager,
                                         @RequestHeader(value = VIEWER_HEADER, required = false) String viewer) {
        if (!canRead(token, manager, viewer)) return SIGN_IN;
        return ResponseEntity.ok(seasons.list());
    }

    @GetMapping("/seasons/{id}")
    public ResponseEntity<?> season(@PathVariable String id,
                                    @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                    @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String manager,
                                    @RequestHeader(value = VIEWER_HEADER, required = false) String viewer) {
        if (!canRead(token, manager, viewer)) return SIGN_IN;
        return seasons.get(id, auth.isValidToken(token), viewer)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such season.")));
    }

    @PostMapping("/seasons")
    public ResponseEntity<?> archive(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                     @RequestBody(required = false) ArchiveRequest req) {
        if (!auth.isValidToken(token)) return FORBIDDEN;
        return ResponseEntity.ok(seasons.archive(req == null ? null : req.name()));
    }

    @DeleteMapping("/seasons/{id}")
    public ResponseEntity<?> deleteSeason(@PathVariable String id,
                                          @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return FORBIDDEN;
        return seasons.delete(id) ? ResponseEntity.ok(Map.of("deleted", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such season."));
    }

    @GetMapping("/ledger")
    public ResponseEntity<?> get(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String manager,
                                 @RequestHeader(value = VIEWER_HEADER, required = false) String viewer) {
        if (!canRead(token, manager, viewer)) return SIGN_IN;
        return ResponseEntity.ok(service.load(auth.isValidToken(token), viewer));
    }

    @PutMapping("/ledger")
    public ResponseEntity<?> put(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @RequestBody LedgerDto body) {
        if (!auth.isValidToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "View-only access. Only the admin can make changes."));
        }
        try {
            return ResponseEntity.ok(service.save(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.LedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LedgerController {

    static final String TOKEN_HEADER = "X-Admin-Token";
    /** The player id a viewer says they are; with viewer access "own", only their payments are sent. */
    static final String VIEWER_HEADER = "X-Viewer";

    private final LedgerService service;
    private final AdminAuth auth;

    public LedgerController(LedgerService service, AdminAuth auth) {
        this.service = service;
        this.auth = auth;
    }

    public record LoginRequest(String name) {
    }

    public record LoginResponse(String name, boolean admin, String token) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        String name = req == null || req.name() == null ? "" : req.name().trim();
        if (auth.isAdminKey(name)) {
            return new LoginResponse("Admin", true, auth.token());
        }
        return new LoginResponse(name.isEmpty() ? "Guest" : name, false, null);
    }

    @GetMapping("/session")
    public Map<String, Boolean> session(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return Map.of("admin", auth.isValidToken(token));
    }

    @GetMapping("/ledger")
    public LedgerDto get(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                         @RequestHeader(value = VIEWER_HEADER, required = false) String viewer) {
        return service.load(auth.isValidToken(token), viewer);
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

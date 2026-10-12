package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Turning notifications on/off per device, sending (admin) and fetching what's waiting. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;
    private final AdminAuth auth;
    /** Tests point devices at a local plain-http push service; real endpoints are always https. */
    private final boolean allowHttp;

    public PushController(PushService push, AdminAuth auth, @Value("${ledger.push-allow-http:false}") boolean allowHttp) {
        this.push = push;
        this.auth = auth;
        this.allowHttp = allowHttp;
    }

    public record SubscribeRequest(String endpoint, String playerId) {
    }

    /** playerIds null sends to every device. */
    public record SendRequest(String title, String body, List<String> playerIds) {
    }

    @GetMapping("/key")
    public Map<String, Object> key() {
        return Map.of("enabled", push.enabled(), "publicKey", push.publicKey());
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                       @RequestBody SubscribeRequest req) {
        if (req == null || !validEndpoint(req.endpoint())) return bad("Not a push endpoint.");
        push.subscribe(req.endpoint(), req.playerId(), auth.isValidToken(token));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody SubscribeRequest req) {
        if (req == null || !validEndpoint(req.endpoint())) return bad("Not a push endpoint.");
        push.unsubscribe(req.endpoint());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                  @RequestBody SendRequest req) {
        if (!auth.isValidToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can send notifications."));
        }
        if (req == null || req.body() == null || req.body().isBlank()) return bad("Write a message first.");
        String title = req.title() == null || req.title().isBlank() ? "Red Falcons" : req.title();
        return ResponseEntity.ok(Map.of("devices", push.send(title, req.body(), req.playerIds())));
    }

    @GetMapping("/pending")
    public List<PushService.Message> pending(@RequestParam String endpoint) {
        return push.pending(endpoint);
    }

    private boolean validEndpoint(String endpoint) {
        return endpoint != null && endpoint.length() <= 2048
                && (endpoint.startsWith("https://") || (allowHttp && endpoint.startsWith("http://")));
    }

    private static ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}

package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The in-app notification feed that viewers and managers see. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record Item(String id, String title, String body, String createdAt, String kind, String author, String matchId, String action) {
    }

    private final NotificationService notifications;
    private final AdminAuth auth;

    public NotificationController(NotificationService notifications, AdminAuth auth) {
        this.notifications = notifications;
        this.auth = auth;
    }

    @GetMapping
    public List<Item> feed() {
        return notifications.latest().stream()
                .map(n -> new Item(n.getId(), n.getTitle(), n.getBody(), n.getVisibleAt(), n.isAnnouncement() ? "announcement" : n.isNotice() ? "notice" : "expense",
                        n.getAuthor(), n.getMatchId(), n.getAction())).toList();
    }

    /** What only the admin should hear about, such as players updating their profiles. */
    @GetMapping("/admin")
    public ResponseEntity<?> adminUpdates(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can see these."));
        return ResponseEntity.ok(notifications.adminUpdates().stream()
                .map(n -> new Item(n.getId(), n.getTitle(), n.getBody(), n.getVisibleAt(), "notice", "", "", "")).toList());
    }
}

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
    private final com.redfalcons.ledger.service.ManagerAccessService managers;

    public NotificationController(NotificationService notifications, AdminAuth auth, com.redfalcons.ledger.service.ManagerAccessService managers) {
        this.notifications = notifications;
        this.auth = auth;
        this.managers = managers;
    }

    /**
     * The bell, for whoever is asking: the admin and managers get the whole feed; a signed-in squad player (their
     * player comes from their sign-in, see AccountFilter) gets the team's notices and their own expenses; guests
     * and anyone not signed in get nothing.
     */
    @GetMapping
    public List<Item> feed(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                           @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String manager,
                           @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer) {
        boolean staff = auth.isValidToken(token) || managers.find(manager).isPresent();
        if (!staff && (viewer == null || viewer.isBlank())) return List.of();
        return notifications.latestFor(staff ? null : viewer).stream()
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

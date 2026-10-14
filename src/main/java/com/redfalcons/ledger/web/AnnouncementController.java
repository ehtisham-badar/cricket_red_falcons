package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.AppNotification;
import com.redfalcons.ledger.service.ManagerAccessService;
import com.redfalcons.ledger.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Announcements: messages the admin or a manager writes (a match reminder, a heads-up before match day, …).
 * They appear in everyone's in-app notifications; they are not push messages. A message can be held back
 * until a later time.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final NotificationService notifications;
    private final ManagerAccessService managers;
    private final AdminAuth auth;

    public AnnouncementController(NotificationService notifications, ManagerAccessService managers, AdminAuth auth) {
        this.notifications = notifications;
        this.managers = managers;
        this.auth = auth;
    }

    public record NewAnnouncement(String title, String body, String matchId, String sendAt) {
    }

    public record Row(String id, String title, String body, String matchId, String author, String authorId,
                      String createdAt, String sendAt, String status) {
        static Row of(AppNotification n) {
            boolean waiting = !n.getPublishAt().isEmpty() && Instant.parse(n.getPublishAt()).isAfter(Instant.now());
            return new Row(n.getId(), n.getTitle(), n.getBody(), n.getMatchId(), n.getAuthor(), n.getAuthorId(), n.getCreatedAt(),
                    n.getPublishAt(), waiting ? "scheduled" : "sent");
        }
    }

    /** Who is writing: the admin, or an approved manager (null for anyone else). */
    private record Actor(String id, String name, boolean admin) {
    }

    private Actor actor(String adminToken, String managerToken) {
        if (auth.isValidToken(adminToken)) return new Actor("admin", "Admin", true);
        return managers.find(managerToken).map(m -> new Actor(m.getId(), m.getName(), false)).orElse(null);
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin and managers can do this."));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                  @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String managerToken) {
        if (actor(adminToken, managerToken) == null) return forbidden();
        return ResponseEntity.ok(notifications.announcements().stream().map(Row::of).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                    @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String managerToken,
                                    @RequestBody NewAnnouncement body) {
        Actor who = actor(adminToken, managerToken);
        if (who == null) return forbidden();
        try {
            return ResponseEntity.ok(Row.of(notifications.announce(who.id(), who.name(), body == null ? null : body.title(),
                    body == null ? null : body.body(), body == null ? null : body.matchId(), body == null ? null : body.sendAt())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                    @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String managerToken,
                                    @PathVariable String id) {
        Actor who = actor(adminToken, managerToken);
        if (who == null) return forbidden();
        return notifications.deleteAnnouncement(id, who.id(), who.admin()) ? ResponseEntity.ok(Map.of("deleted", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such announcement, or it isn't yours to delete."));
    }
}

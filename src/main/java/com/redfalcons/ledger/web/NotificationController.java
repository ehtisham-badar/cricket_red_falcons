package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The in-app notification feed that viewers and managers see. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record Item(String id, String title, String body, String createdAt) {
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Item> feed() {
        return notifications.latest().stream().map(n -> new Item(n.getId(), n.getTitle(), n.getBody(), n.getCreatedAt())).toList();
    }
}

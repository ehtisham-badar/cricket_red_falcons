package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Something the admin did that viewers and managers should hear about, e.g. a new expense. */
@Entity
@Table(name = "app_notifications")
public class AppNotification {

    @Id
    @Column(length = 64)
    private String id;

    /** What this is about ("expense:<id>"), so editing the same expense updates it instead of repeating it. */
    @Column(name = "ref_key", nullable = false, length = 100)
    private String refKey;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    protected AppNotification() {
    }

    public AppNotification(String id, String refKey, String title, String body, String createdAt) {
        this.id = id;
        this.refKey = refKey;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public void setText(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public String getId() { return id; }
    public String getRefKey() { return refKey; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
}

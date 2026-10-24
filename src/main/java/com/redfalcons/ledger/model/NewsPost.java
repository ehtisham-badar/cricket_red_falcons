package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A news post on the News tab: written by the admin, read by everyone. Pinned posts stay on top. */
@Entity
@Table(name = "news_posts")
public class NewsPost {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String title;

    /** A short label such as "Rules" or "Fixtures" ('' for none). */
    @Column(nullable = false, length = 30)
    private String tag = "";

    @Column(nullable = false, length = 8000)
    private String body;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "updated_at", nullable = false, length = 40)
    private String updatedAt;

    private boolean pinned;

    protected NewsPost() {
    }

    public NewsPost(String id, String title, String tag, String body, boolean pinned, String now) {
        this.id = id;
        this.createdAt = now;
        edit(title, tag, body, pinned, now);
    }

    public void edit(String title, String tag, String body, boolean pinned, String now) {
        this.title = title;
        this.tag = tag;
        this.body = body;
        this.pinned = pinned;
        this.updatedAt = now;
    }

    /** Pinning isn't an edit, so it leaves updatedAt alone. */
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getTag() { return tag; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public boolean isPinned() { return pinned; }
}

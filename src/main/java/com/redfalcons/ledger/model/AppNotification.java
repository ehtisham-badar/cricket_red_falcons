package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * Something viewers and managers should hear about: a new expense, or an announcement the admin or a manager
 * wrote (optionally about a match, and optionally held back until a later time).
 */
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

    /** "announcement" for written ones; null (older rows) means an expense notice. */
    @Column(length = 16)
    private String kind;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(length = 80)
    private String author;

    @Column(name = "match_id", length = 64)
    private String matchId;

    /** When it appears for everyone; null means as soon as it was made. */
    @Column(name = "publish_at", length = 40)
    private String publishAt;

    /** False while a scheduled announcement still has to be pushed to phones; null (older rows) means nothing to push. */
    @Column(name = "push_done")
    private Boolean pushDone;

    protected AppNotification() {
    }

    public static AppNotification announcement(String id, String authorId, String author, String title, String body,
                                               String matchId, String publishAt, String createdAt) {
        AppNotification n = new AppNotification(id, "announcement:" + id, title, body, createdAt);
        n.kind = "announcement";
        n.authorId = authorId;
        n.author = author;
        n.matchId = matchId;
        n.publishAt = publishAt;
        n.pushDone = publishAt == null;
        return n;
    }

    /** A notice the app raises by itself, e.g. the Playing XI being announced. */
    public static AppNotification notice(String id, String refKey, String title, String body, String matchId, String createdAt) {
        AppNotification n = new AppNotification(id, refKey, title, body, createdAt);
        n.kind = "notice";
        n.matchId = matchId;
        return n;
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
    public boolean isNotice() { return "notice".equals(kind); }
    public boolean awaitingPush() { return Boolean.FALSE.equals(pushDone); }
    public void markPushed() { this.pushDone = true; }
    public boolean isAnnouncement() { return "announcement".equals(kind); }
    public String getAuthorId() { return authorId == null ? "" : authorId; }
    public String getAuthor() { return author == null ? "" : author; }
    public String getMatchId() { return matchId == null ? "" : matchId; }
    public String getPublishAt() { return publishAt == null ? "" : publishAt; }

    /** When it shows up for everyone: the scheduled time, else when it was made. */
    public String getVisibleAt() { return publishAt == null || publishAt.isEmpty() ? createdAt : publishAt; }
}

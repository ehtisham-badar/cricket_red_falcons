package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A signed-in player browser. The id is a hash of the session token; the token itself is never stored. */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "expires_at", nullable = false, length = 40)
    private String expiresAt;

    protected UserSession() {
    }

    public UserSession(String id, String userId, String label, String createdAt, String expiresAt) {
        this.id = id;
        this.userId = userId;
        this.label = label;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getCreatedAt() { return createdAt; }
    public String getExpiresAt() { return expiresAt; }
}

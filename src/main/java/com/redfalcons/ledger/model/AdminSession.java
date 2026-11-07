package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A signed-in admin browser. The id is a hash of its session token; the token itself is never stored. */
@Entity
@Table(name = "admin_sessions")
public class AdminSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    /** The account whose admin rights opened this session; null for a sign-in with the admin key. */
    @Column(name = "account_id", length = 64)
    private String accountId;

    protected AdminSession() {
    }

    public AdminSession(String id, String label, String createdAt) {
        this.id = id;
        this.label = label;
        this.createdAt = createdAt;
    }

    public AdminSession(String id, String label, String createdAt, String accountId) {
        this(id, label, createdAt);
        this.accountId = accountId;
    }

    public String getAccountId() { return accountId; }
    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getCreatedAt() { return createdAt; }
}

package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A signed-in manager browser. The id is a hash of its session token; the token itself is never stored. */
@Entity
@Table(name = "manager_sessions")
public class ManagerSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "manager_id", nullable = false, length = 64)
    private String managerId;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    protected ManagerSession() {
    }

    public ManagerSession(String id, String managerId, String createdAt) {
        this.id = id;
        this.managerId = managerId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getManagerId() { return managerId; }
}

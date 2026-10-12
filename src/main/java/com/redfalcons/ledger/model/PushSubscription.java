package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A device that turned notifications on. The id is a hash of its push endpoint. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    /** The player this device belongs to ('' when unknown). */
    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId = "";

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(String id, String endpoint, String playerId, boolean admin, String createdAt) {
        this.id = id;
        this.endpoint = endpoint;
        this.playerId = playerId;
        this.admin = admin;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public String getPlayerId() { return playerId; }
    public boolean isAdmin() { return admin; }
}

package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A notification waiting for one device; the device fetches it when its (empty) push arrives. */
@Entity
@Table(name = "push_messages")
public class PushMessage {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "subscription_id", nullable = false, length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    private boolean delivered;

    protected PushMessage() {
    }

    public PushMessage(String id, String subscriptionId, String title, String body, String createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
    public boolean isDelivered() { return delivered; }
    public void markDelivered() { this.delivered = true; }
}

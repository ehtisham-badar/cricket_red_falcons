package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A browser that passed two-factor and may skip the code until it expires. The id hashes its device token. */
@Entity
@Table(name = "trusted_devices")
public class TrustedDevice {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "last_used", nullable = false, length = 40)
    private String lastUsed;

    @Column(name = "expires_at", nullable = false, length = 40)
    private String expiresAt;

    protected TrustedDevice() {
    }

    public TrustedDevice(String id, String label, String createdAt, String expiresAt) {
        this.id = id;
        this.label = label;
        this.createdAt = createdAt;
        this.lastUsed = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getCreatedAt() { return createdAt; }
    public String getLastUsed() { return lastUsed; }
    public String getExpiresAt() { return expiresAt; }
    public void used(String now) { this.lastUsed = now; }
}

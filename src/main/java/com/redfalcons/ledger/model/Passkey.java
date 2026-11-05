package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * A biometric sign-in (Face ID, Touch ID, fingerprint) registered on one device, as a WebAuthn credential:
 * the device keeps the private key and only unlocks it with the person's biometrics; we keep the public key.
 */
@Entity
@Table(name = "passkeys", indexes = @Index(columnList = "user_id"))
public class Passkey {

    /** The credential id, base64url. */
    @Id
    @Column(length = 512)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** The public key, X.509 SubjectPublicKeyInfo (DER). */
    @Column(name = "public_key", nullable = false, length = 1200)
    private byte[] publicKey;

    /** COSE algorithm: -7 (ES256) or -257 (RS256). */
    @Column(nullable = false)
    private int alg;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "last_used_at", nullable = false, length = 40)
    private String lastUsedAt = "";

    protected Passkey() {
    }

    public Passkey(String id, String userId, byte[] publicKey, int alg, long signCount, String label, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.publicKey = publicKey;
        this.alg = alg;
        this.signCount = signCount;
        this.label = label;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public byte[] getPublicKey() { return publicKey; }
    public int getAlg() { return alg; }
    public long getSignCount() { return signCount; }
    public String getLabel() { return label; }
    public String getCreatedAt() { return createdAt; }
    public String getLastUsedAt() { return lastUsedAt == null ? "" : lastUsedAt; }

    public void used(long signCount, String at) {
        this.signCount = signCount;
        this.lastUsedAt = at;
    }
}

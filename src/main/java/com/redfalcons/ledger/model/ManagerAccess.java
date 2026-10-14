package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * An app manager: a viewer the admin trusts to set the playing XI and nothing else. The viewer picks a
 * phrase when asking; once the admin approves, typing that phrase on the start screen signs them in (no
 * two-factor). The admin can see and change the phrase at any time, so it is kept as typed.
 */
@Entity
@Table(name = "manager_access")
public class ManagerAccess {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String DECLINED = "declined";

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false, length = 80)
    private String phrase;

    /** Hash of the secret the requesting browser keeps, so it can see whether it was approved or withdraw. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "decided_at", length = 40)
    private String decidedAt;

    /** Why the admin declined, if they said. */
    @Column(length = 300)
    private String note;

    protected ManagerAccess() {
    }

    public ManagerAccess(String id, String name, String label, String phrase, String requestHash, String createdAt) {
        this.id = id;
        this.name = name;
        this.label = label;
        this.phrase = phrase;
        this.requestHash = requestHash;
        this.status = PENDING;
        this.createdAt = createdAt;
    }

    public void approve(String at) {
        this.status = APPROVED;
        this.decidedAt = at;
    }

    public void decline(String note, String at) {
        this.status = DECLINED;
        this.note = note;
        this.decidedAt = at;
    }

    public void setPhrase(String phrase) { this.phrase = phrase; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLabel() { return label; }
    public String getPhrase() { return phrase; }
    public String getRequestHash() { return requestHash; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getDecidedAt() { return decidedAt; }
    public String getNote() { return note; }
}

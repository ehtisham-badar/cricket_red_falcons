package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** "Forgot password": waits in the admin's requests until they reset the password (or decline). */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    /** "open", "done" or "declined". */
    @Column(nullable = false, length = 10)
    private String status = "open";

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(String id, String userId, String note, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.note = note;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getNote() { return note; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

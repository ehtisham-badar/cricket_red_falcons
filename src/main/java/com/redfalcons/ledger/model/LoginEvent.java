package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** One sign-in attempt on an account, for the admin's record: when, how, from which browser, and whether it worked. */
@Entity
@Table(name = "login_events", indexes = @Index(columnList = "user_id"))
public class LoginEvent {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "at_time", nullable = false, length = 40)
    private String at;

    /** "password", "biometric" or "signup". */
    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private boolean ok;

    protected LoginEvent() {
    }

    public LoginEvent(String id, String userId, String at, String method, String label, boolean ok) {
        this.id = id;
        this.userId = userId;
        this.at = at;
        this.method = method;
        this.label = label;
        this.ok = ok;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getAt() { return at; }
    public String getMethod() { return method; }
    public String getLabel() { return label; }
    public boolean isOk() { return ok; }
}

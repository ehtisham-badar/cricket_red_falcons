package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * A player's sign-in: email, password and full name. When the name is a squad player's (or they confirm
 * A name that is exactly a squad player's links the account ({@code playerId}) and they see the app as that
 * player. Any other account sees only the Home tab until the admin links it to a squad player from Requests.
 * ({@code pendingPlayerId} is a player they once asked to be, kept only as the admin's suggestion.) The password
 * is stored as a salted hash.
 */
@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserAccount {

    @Id
    @Column(length = 64)
    private String id;

    /** Lower case. */
    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** The squad player this account is, or '' when it isn't linked. */
    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId = "";

    /** The squad player this account asked to be, waiting for the admin's approval ('' when none). */
    @Column(name = "pending_player_id", length = 64)
    private String pendingPlayerId = "";

    /** The admin said this account isn't anyone in the squad: it stays on Home only, off the Requests list. */
    @Column(name = "dismissed")
    private Boolean dismissed = false;

    /** Staff rights the admin gave this account: "" (a player), "manager" (sets the playing XI, announcements, polls) or "admin". */
    @Column(name = "staff_role", length = 16)
    private String role = "";

    /** A guest's own profile, if they make one (players keep theirs on their squad player). '' when not set. */
    @Column(name = "profile_role", length = 30)
    private String profileRole = "";
    @Column(name = "batting_hand", length = 10)
    private String battingHand = "";
    @Column(name = "bowling_arm", length = 10)
    private String bowlingArm = "";
    @Column(name = "bowling_style", length = 30)
    private String bowlingStyle = "";
    @Column(length = 200)
    private String about = "";

    /** Set when the admin has reset the password: the next sign-in must choose a new one. */
    @Column(name = "must_change", nullable = false)
    private boolean mustChange;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "last_login_at", nullable = false, length = 40)
    private String lastLoginAt = "";

    /** "password" or "biometric". */
    @Column(name = "last_login_method", nullable = false, length = 20)
    private String lastLoginMethod = "";

    @Column(name = "login_count", nullable = false)
    private int loginCount;

    /** Wrong passwords in a row; after too many the account waits until {@code lockedUntil}. */
    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "locked_until", nullable = false, length = 40)
    private String lockedUntil = "";

    protected UserAccount() {
    }

    public UserAccount(String id, String email, String name, String passwordHash, String createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public String getPlayerId() { return playerId == null ? "" : playerId; }
    public String getPendingPlayerId() { return pendingPlayerId == null ? "" : pendingPlayerId; }
    public boolean isDismissed() { return Boolean.TRUE.equals(dismissed); }
    public String getProfileRole() { return profileRole == null ? "" : profileRole; }
    public String getBattingHand() { return battingHand == null ? "" : battingHand; }
    public String getBowlingArm() { return bowlingArm == null ? "" : bowlingArm; }
    public String getBowlingStyle() { return bowlingStyle == null ? "" : bowlingStyle; }
    public String getAbout() { return about == null ? "" : about; }

    public void setProfile(String role, String battingHand, String bowlingArm, String bowlingStyle, String about) {
        this.profileRole = role;
        this.battingHand = battingHand;
        this.bowlingArm = bowlingArm;
        this.bowlingStyle = bowlingStyle;
        this.about = about;
    }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }
    public boolean isMustChange() { return mustChange; }
    public String getRole() { return role == null ? "" : role; }
    public void setRole(String role) { this.role = role == null ? "" : role; }
    public String getCreatedAt() { return createdAt; }
    public String getLastLoginAt() { return lastLoginAt == null ? "" : lastLoginAt; }
    public String getLastLoginMethod() { return lastLoginMethod == null ? "" : lastLoginMethod; }
    public int getLoginCount() { return loginCount; }
    public int getFailedCount() { return failedCount; }
    public String getLockedUntil() { return lockedUntil == null ? "" : lockedUntil; }

    public void setName(String name) { this.name = name; }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public void setPlayerId(String playerId) { this.playerId = playerId == null ? "" : playerId; }
    public void setPendingPlayerId(String id) { this.pendingPlayerId = id == null ? "" : id; }
    public void setMustChange(boolean mustChange) { this.mustChange = mustChange; }

    public void loggedIn(String at, String method) {
        this.lastLoginAt = at;
        this.lastLoginMethod = method;
        this.loginCount += 1;
        this.failedCount = 0;
        this.lockedUntil = "";
    }

    public void failed(int count, String lockedUntil) {
        this.failedCount = count;
        this.lockedUntil = lockedUntil == null ? "" : lockedUntil;
    }
}

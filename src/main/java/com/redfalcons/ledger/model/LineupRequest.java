package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * A manager's proposed playing XI and 12th man for one match. Nothing changes until the admin approves it;
 * a decline can carry a note back to the manager.
 */
@Entity
@Table(name = "lineup_requests")
public class LineupRequest {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String DECLINED = "declined";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "manager_id", nullable = false, length = 64)
    private String managerId;

    @Column(name = "manager_name", nullable = false, length = 80)
    private String managerName;

    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    /** Player ids, comma separated. */
    @Column(nullable = false, length = 2000)
    private String playing;

    /** The 12th man's player id, or empty. */
    @Column(nullable = false, length = 64)
    private String twelfth;

    /** Batting order (player ids, comma separated); empty or null means the default order. */
    @Column(length = 2000)
    private String batting;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    @Column(name = "decided_at", length = 40)
    private String decidedAt;

    protected LineupRequest() {
    }

    public LineupRequest(String id, String managerId, String managerName, String matchId, String playing, String twelfth,
                         String batting, String createdAt) {
        this.id = id;
        this.managerId = managerId;
        this.managerName = managerName;
        this.matchId = matchId;
        this.playing = playing;
        this.twelfth = twelfth;
        this.batting = batting;
        this.status = PENDING;
        this.createdAt = createdAt;
    }

    public void decide(String status, String note, String at) {
        this.status = status;
        this.note = note;
        this.decidedAt = at;
    }

    public String getId() { return id; }
    public String getManagerId() { return managerId; }
    public String getManagerName() { return managerName; }
    public String getMatchId() { return matchId; }
    public String getPlaying() { return playing; }
    public String getTwelfth() { return twelfth; }
    public String getBatting() { return batting == null ? "" : batting; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public String getCreatedAt() { return createdAt; }
    public String getDecidedAt() { return decidedAt; }
}

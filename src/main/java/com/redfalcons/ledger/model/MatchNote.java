package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** The captain's private plan for a match; only the admin and managers can read it. */
@Entity
@Table(name = "match_notes")
public class MatchNote {

    @Id
    @Column(name = "match_id", length = 64)
    private String matchId;

    @Column(name = "field_plan", length = 1500)
    private String fieldPlan;

    @Column(name = "batting_plan", length = 1500)
    private String battingPlan;

    @Column(name = "opposition", length = 1500)
    private String opposition;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", length = 40)
    private String updatedAt;

    protected MatchNote() {
    }

    public MatchNote(String matchId, String fieldPlan, String battingPlan, String opposition, String updatedBy, String updatedAt) {
        this.matchId = matchId;
        this.fieldPlan = fieldPlan;
        this.battingPlan = battingPlan;
        this.opposition = opposition;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getMatchId() { return matchId; }
    public String getFieldPlan() { return fieldPlan; }
    public String getBattingPlan() { return battingPlan; }
    public String getOpposition() { return opposition; }
    public String getUpdatedBy() { return updatedBy; }
    public String getUpdatedAt() { return updatedAt; }
}

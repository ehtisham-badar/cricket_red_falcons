package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Voting for man of the match has been closed for this match, so its winner is announced. */
@Entity
@Table(name = "mom_closed")
public class MomClosed {

    @Id
    @Column(name = "match_id", length = 64)
    private String matchId;

    @Column(name = "closed_at", length = 40)
    private String closedAt;

    protected MomClosed() {
    }

    public MomClosed(String matchId, String closedAt) {
        this.matchId = matchId;
        this.closedAt = closedAt;
    }

    public String getMatchId() { return matchId; }
    public String getClosedAt() { return closedAt; }
}

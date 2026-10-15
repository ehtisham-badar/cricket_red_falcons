package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** One player's pick for man of the match; replaced if they change their mind. */
@Entity
@Table(name = "match_votes")
public class MatchVote {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "match_id", length = 64)
    private String matchId;

    @Column(name = "voter_id", length = 64)
    private String voterId;

    @Column(name = "candidate_id", length = 64)
    private String candidateId;

    @Column(name = "created_at", length = 40)
    private String createdAt;

    protected MatchVote() {
    }

    public MatchVote(String id, String matchId, String voterId, String candidateId, String createdAt) {
        this.id = id;
        this.matchId = matchId;
        this.voterId = voterId;
        this.candidateId = candidateId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMatchId() { return matchId; }
    public String getVoterId() { return voterId; }
    public String getCandidateId() { return candidateId; }
    public String getCreatedAt() { return createdAt; }
}

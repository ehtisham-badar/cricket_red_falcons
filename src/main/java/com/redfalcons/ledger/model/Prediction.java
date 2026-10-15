package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A player's guess for who wins a match: "win" or "loss". */
@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "match_id", length = 64)
    private String matchId;

    @Column(name = "player_id", length = 64)
    private String playerId;

    @Column(name = "pick", length = 8)
    private String pick;

    @Column(name = "created_at", length = 40)
    private String createdAt;

    protected Prediction() {
    }

    public Prediction(String id, String matchId, String playerId, String pick, String createdAt) {
        this.id = id;
        this.matchId = matchId;
        this.playerId = playerId;
        this.pick = pick;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMatchId() { return matchId; }
    public String getPlayerId() { return playerId; }
    public String getPick() { return pick; }
    public String getCreatedAt() { return createdAt; }
}

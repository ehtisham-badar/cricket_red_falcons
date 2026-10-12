package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** One player's batting, bowling and fielding in one match. Runs are the sum of the scoring shots. */
@Entity
@Table(name = "match_stats")
public class MatchStat {

    /** matchId + ":" + playerId */
    @Id
    @Column(length = 140)
    private String id;

    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    private int balls;
    private int ones;
    private int twos;
    private int threes;
    private int fours;
    private int sixes;
    @Column(name = "is_out")
    private boolean out;

    @Column(name = "bowl_balls")
    private int bowlBalls;
    @Column(name = "bowl_runs")
    private int bowlRuns;
    private int wickets;

    private int catches;
    private int drops;
    private int misfields;
    @Column(name = "good_fielding")
    private int goodFielding;

    protected MatchStat() {
    }

    public MatchStat(String matchId, String playerId, int[] n, boolean out) {
        this.id = matchId + ":" + playerId;
        this.matchId = matchId;
        this.playerId = playerId;
        this.balls = n[0]; this.ones = n[1]; this.twos = n[2]; this.threes = n[3]; this.fours = n[4]; this.sixes = n[5];
        this.bowlBalls = n[6]; this.bowlRuns = n[7]; this.wickets = n[8];
        this.catches = n[9]; this.drops = n[10]; this.misfields = n[11]; this.goodFielding = n[12];
        this.out = out;
    }

    public String getId() { return id; }
    public String getMatchId() { return matchId; }
    public String getPlayerId() { return playerId; }
    public int[] numbers() {
        return new int[] { balls, ones, twos, threes, fours, sixes, bowlBalls, bowlRuns, wickets, catches, drops, misfields, goodFielding };
    }
    public boolean isOut() { return out; }
}

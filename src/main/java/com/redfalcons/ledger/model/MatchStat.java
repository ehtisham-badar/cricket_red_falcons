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
    /** Extras the player gave away as a bowler; nullable so the columns could be added to existing rows. */
    private Integer wides;
    @Column(name = "no_balls")
    private Integer noBalls;

    /** How a dismissed batter got out: bowled, caught, lbw, runout, stumped, hitwicket, retired, other, or empty. */
    @Column(length = 12)
    private String dismissal;
    /** Names are free text because the bowler and fielder are usually from the other team. */
    @Column(name = "dismissed_by", length = 40)
    private String dismissedBy;
    @Column(length = 40)
    private String fielder;
    /** The team score when this batter was out, and the over, for the fall of wickets. */
    @Column(name = "fow_score")
    private Integer fowScore;
    @Column(name = "fow_over", length = 8)
    private String fowOver;

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
        this.wides = n[13]; this.noBalls = n[14];
        this.out = out;
    }

    public String getId() { return id; }
    public String getMatchId() { return matchId; }
    public String getPlayerId() { return playerId; }
    public int[] numbers() {
        return new int[] { balls, ones, twos, threes, fours, sixes, bowlBalls, bowlRuns, wickets, catches, drops, misfields, goodFielding,
                wides == null ? 0 : wides, noBalls == null ? 0 : noBalls };
    }
    public boolean isOut() { return out; }

    public void setHow(String dismissal, String dismissedBy, String fielder, Integer fowScore, String fowOver) {
        this.dismissal = dismissal;
        this.dismissedBy = dismissedBy;
        this.fielder = fielder;
        this.fowScore = fowScore;
        this.fowOver = fowOver;
    }

    public String getDismissal() { return dismissal == null ? "" : dismissal; }
    public String getDismissedBy() { return dismissedBy == null ? "" : dismissedBy; }
    public String getFielder() { return fielder == null ? "" : fielder; }
    public Integer getFowScore() { return fowScore; }
    public String getFowOver() { return fowOver == null ? "" : fowOver; }
}

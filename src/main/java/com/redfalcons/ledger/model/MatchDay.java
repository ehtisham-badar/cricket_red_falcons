package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A league match, added when it is played; Sr. no. = position + 1. */
@Entity
@Table(name = "matches")
public class MatchDay {

    @Id
    @Column(length = 64)
    private String id;

    private int position;

    /** ISO date (YYYY-MM-DD), or '' when not set. */
    @Column(name = "match_date", nullable = false, length = 10)
    private String date = "";

    @Column(nullable = false, length = 80)
    private String opponent = "";

    @Column(nullable = false, length = 80)
    private String venue = "";

    /** Only finished matches charge their XI (league share and expenses). Null on older rows means no. */
    private Boolean finished;

    /** A generated test match: never charged, and removed all at once by the admin. Null means a real match. */
    @Column(name = "is_test")
    private Boolean test;

    /** Kick-off time as HH:MM, or '' (null on older rows) until it is confirmed. */
    @Column(name = "match_time", length = 5)
    private String time;

    /** The admin has announced the Playing XI, so it shows on everyone's home screen. Null means no. */
    @Column(name = "xi_announced")
    private Boolean announced;

    /** How the match ended for the Red Falcons: "win", "loss", "tie", or null/empty until the admin enters it. */
    @Column(name = "match_result", length = 8)
    private String result;

    /** Extras the opposition gave in our innings: "byes,legByes,wides,noBalls"; null/empty until entered. */
    @Column(name = "our_extras", length = 24)
    private String ourExtras;

    protected MatchDay() {
    }

    public MatchDay(String id, int position, String date, String opponent, String venue, boolean finished, boolean test,
                    String time, boolean announced, String result) {
        this.id = id;
        this.position = position;
        this.date = date;
        this.opponent = opponent;
        this.venue = venue;
        this.finished = finished;
        this.test = test;
        this.time = time;
        this.announced = announced;
        this.result = result;
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getDate() { return date; }
    public String getOpponent() { return opponent; }
    public String getVenue() { return venue; }
    public boolean isFinished() { return Boolean.TRUE.equals(finished); }
    public boolean isTest() { return Boolean.TRUE.equals(test); }
    public String getTime() { return time == null ? "" : time; }
    public String getOurExtras() { return ourExtras == null ? "" : ourExtras; }
    public void setOurExtras(String v) { this.ourExtras = v; }
    public String getResult() { return result == null ? "" : result; }
    public boolean isAnnounced() { return Boolean.TRUE.equals(announced); }
}

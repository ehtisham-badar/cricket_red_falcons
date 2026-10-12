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

    protected MatchDay() {
    }

    public MatchDay(String id, int position, String date, String opponent, String venue) {
        this.id = id;
        this.position = position;
        this.date = date;
        this.opponent = opponent;
        this.venue = venue;
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getDate() { return date; }
    public String getOpponent() { return opponent; }
    public String getVenue() { return venue; }
}

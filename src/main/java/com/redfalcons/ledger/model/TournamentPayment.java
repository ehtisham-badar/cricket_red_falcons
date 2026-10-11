package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Money the admin has sent to the tournament management towards the league fee. */
@Entity
@Table(name = "tournament_payments")
public class TournamentPayment {

    @Id
    @Column(length = 64)
    private String id;

    private int position;

    /** ISO date (YYYY-MM-DD), or '' when unknown. */
    @Column(name = "paid_on", nullable = false, length = 10)
    private String date = "";

    @Column(nullable = false)
    private String amount = "";

    @Column(nullable = false, length = 40)
    private String method = "";

    @Column(nullable = false, length = 200)
    private String note = "";

    protected TournamentPayment() {
    }

    public TournamentPayment(String id, int position, String date, String amount, String method, String note) {
        this.id = id;
        this.position = position;
        this.date = date;
        this.amount = amount;
        this.method = method;
        this.note = note;
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getDate() { return date; }
    public String getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getNote() { return note; }
}

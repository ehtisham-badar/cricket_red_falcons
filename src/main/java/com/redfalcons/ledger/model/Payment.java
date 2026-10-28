package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** One payment a player made towards the series (instalments add up to their "paid" total). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    private int position;

    /** ISO date (YYYY-MM-DD), or '' when unknown (e.g. amounts entered before payments were tracked). */
    @Column(name = "paid_on", nullable = false, length = 10)
    private String date = "";

    @Column(nullable = false)
    private String amount = "";

    @Column(nullable = false, length = 40)
    private String method = "";

    @Column(nullable = false, length = 200)
    private String note = "";

    /** Money the admin paid back to the player (it lowers what they have paid); null on rows from before this column. */
    @Column(name = "paid_back")
    private Boolean refund;

    protected Payment() {
    }

    public Payment(String id, String playerId, int position, String date, String amount, String method, String note,
                   boolean refund) {
        this.id = id;
        this.playerId = playerId;
        this.position = position;
        this.date = date;
        this.amount = amount;
        this.method = method;
        this.note = note;
        this.refund = refund;
    }

    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public int getPosition() { return position; }
    public String getDate() { return date; }
    public String getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getNote() { return note; }
    public boolean isRefund() { return Boolean.TRUE.equals(refund); }
}

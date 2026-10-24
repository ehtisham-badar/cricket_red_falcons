package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * One item of spending on a day (ground fee, balls, water…), paid by the team fund or a player.
 * Usually everyone who played shares it; {@code sharedBy} narrows that to chosen players (a breakfast only some had).
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "day_id", nullable = false, length = 64)
    private String dayId;

    private int position;

    @Column(nullable = false)
    private String item = "";

    @Column(nullable = false)
    private String amount = "";

    /** '' = paid from the team fund, otherwise the id of the player who paid out of pocket. */
    @Column(name = "paid_by", nullable = false, length = 64)
    private String paidBy = "";

    /** '' = shared by everyone who played; otherwise comma-separated ids of the players who share it. */
    @Column(name = "shared_by", length = 2000)
    private String sharedBy = "";

    protected Expense() {
    }

    public Expense(String id, String dayId, int position, String item, String amount, String paidBy, String sharedBy) {
        this.id = id;
        this.dayId = dayId;
        this.position = position;
        this.item = item;
        this.amount = amount;
        this.paidBy = paidBy;
        this.sharedBy = sharedBy;
    }

    public String getId() { return id; }
    public String getDayId() { return dayId; }
    public int getPosition() { return position; }
    public String getItem() { return item; }
    public String getAmount() { return amount; }
    public String getPaidBy() { return paidBy; }
    public String getSharedBy() { return sharedBy == null ? "" : sharedBy; }
}

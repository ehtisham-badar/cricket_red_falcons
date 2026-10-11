package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** One item of spending on a day (ground fee, balls, water…), paid by the team fund or a player. */
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

    protected Expense() {
    }

    public Expense(String id, String dayId, int position, String item, String amount, String paidBy) {
        this.id = id;
        this.dayId = dayId;
        this.position = position;
        this.item = item;
        this.amount = amount;
        this.paidBy = paidBy;
    }

    public String getId() { return id; }
    public String getDayId() { return dayId; }
    public int getPosition() { return position; }
    public String getItem() { return item; }
    public String getAmount() { return amount; }
    public String getPaidBy() { return paidBy; }
}

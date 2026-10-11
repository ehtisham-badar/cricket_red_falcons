package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Amount spent on a day (training or match), keyed by day id. */
@Entity
@Table(name = "day_amounts")
public class DayAmount {

    @Id
    @Column(name = "day_id", length = 64)
    private String dayId;

    @Column(nullable = false)
    private String amount = "";

    protected DayAmount() {
    }

    public DayAmount(String dayId, String amount) {
        this.dayId = dayId;
        this.amount = amount;
    }

    public String getDayId() { return dayId; }
    public String getAmount() { return amount; }
}

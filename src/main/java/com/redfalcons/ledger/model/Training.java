package com.redfalcons.ledger.model;

import jakarta.persistence.*;

@Entity
@Table(name = "trainings")
public class Training {

    @Id
    @Column(length = 64)
    private String id;

    private int position;

    @Column(name = "description", nullable = false, length = 1000)
    private String desc = "";

    protected Training() {
    }

    public Training(String id, int position, String desc) {
        this.id = id;
        this.position = position;
        this.desc = desc;
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getDesc() { return desc; }
}

package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A finished season: a full copy of the ledger (as JSON) kept when a new series starts. */
@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    /** ISO instant the season was archived. */
    @Column(name = "archived_at", nullable = false, length = 40)
    private String archivedAt;

    @Lob
    @Column(nullable = false)
    private String data;

    protected Season() {
    }

    public Season(String id, String name, String archivedAt, String data) {
        this.id = id;
        this.name = name;
        this.archivedAt = archivedAt;
        this.data = data;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArchivedAt() { return archivedAt; }
    public String getData() { return data; }
}

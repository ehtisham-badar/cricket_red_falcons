package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** The last league summary read from Crickslab (one row), so the app is served from our own database. */
@Entity
@Table(name = "league_snapshots")
public class LeagueSnapshot {

    @Id
    @Column(length = 20)
    private String id;

    /** The summary as JSON. */
    @Lob
    @Column(nullable = false)
    private String data;

    /** When it was read from Crickslab (ISO instant). */
    @Column(name = "fetched_at", nullable = false, length = 40)
    private String fetchedAt;

    protected LeagueSnapshot() {
    }

    public LeagueSnapshot(String id, String data, String fetchedAt) {
        this.id = id;
        this.data = data;
        this.fetchedAt = fetchedAt;
    }

    public String getId() { return id; }
    public String getData() { return data; }
    public String getFetchedAt() { return fetchedAt; }
}

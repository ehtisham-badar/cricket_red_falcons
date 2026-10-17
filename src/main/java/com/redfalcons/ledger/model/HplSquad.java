package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Another HPL team's squad, typed in by the admin: one player per line ("Name (c) - Batsman"). */
@Entity
@Table(name = "hpl_squads")
public class HplSquad {

    /** The team name in lower case with only letters and digits, so "RAGEROVERS" and "Rage Rovers" match. */
    @Id
    @Column(name = "team_key", length = 60)
    private String key;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 4000)
    private String players;

    @Column(name = "updated_at", nullable = false, length = 40)
    private String updatedAt;

    protected HplSquad() {
    }

    public HplSquad(String key, String name, String players, String now) {
        this.key = key;
        edit(name, players, now);
    }

    public void edit(String name, String players, String now) {
        this.name = name;
        this.players = players;
        this.updatedAt = now;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getPlayers() { return players; }
    public String getUpdatedAt() { return updatedAt; }
}

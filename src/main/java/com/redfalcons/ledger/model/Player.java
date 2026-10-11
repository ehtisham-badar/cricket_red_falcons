package com.redfalcons.ledger.model;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(length = 64)
    private String id;

    /** 0-based position; Sr. no. = position + 1. */
    private int position;

    @Column(nullable = false)
    private String name;

    @Column(name = "player_role", nullable = false)
    private String role = "";

    @Column(nullable = false)
    private String paid = "";

    /** Whether the player shares the tournament fee. Null (rows from before this column) means yes. */
    @Column(name = "in_tournament")
    private Boolean inTournament = true;

    /** Ids of the days ('t1', 'm1'…) this player attended. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_attendance", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "day_id", length = 64)
    private Set<String> attended = new LinkedHashSet<>();

    protected Player() {
    }

    public Player(String id, int position, String name, String role, String paid, boolean inTournament,
                  Set<String> attended) {
        this.id = id;
        this.position = position;
        this.name = name;
        this.role = role;
        this.paid = paid;
        this.inTournament = inTournament;
        this.attended = new LinkedHashSet<>(attended);
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPaid() { return paid; }
    public boolean isInTournament() { return !Boolean.FALSE.equals(inTournament); }
    public Set<String> getAttended() { return attended; }
}

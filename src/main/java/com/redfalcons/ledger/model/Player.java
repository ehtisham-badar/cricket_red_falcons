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

    /** WhatsApp number for reminders; admin-only. Null on rows from before this column. */
    private String phone = "";

    /** Profile details; null on rows from before they existed. */
    @Column(name = "batting_hand", length = 8)
    private String battingHand;

    @Column(name = "bowling_arm", length = 8)
    private String bowlingArm;

    @Column(name = "bowling_style", length = 40)
    private String bowlingStyle;

    private Boolean keeper;
    private Boolean captain;

    @Column(name = "vice_captain")
    private Boolean viceCaptain;

    @Column(length = 3)
    private String jersey;

    /** The name printed on the player's jersey. */
    @Column(name = "jersey_name", length = 24)
    private String jerseyName;

    /** When the player last pressed Save on their own profile (ISO instant), or null. */
    @Column(name = "profile_saved_at", length = 40)
    private String profileSavedAt;

    /** Ids of the days ('t1', 'm1'…) this player attended. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_attendance", joinColumns = @JoinColumn(name = "player_id"))
    @Column(name = "day_id", length = 64)
    private Set<String> attended = new LinkedHashSet<>();

    protected Player() {
    }

    public Player(String id, int position, String name, String role, String paid, boolean inTournament,
                  String phone, Set<String> attended) {
        this.id = id;
        this.position = position;
        this.name = name;
        this.role = role;
        this.paid = paid;
        this.inTournament = inTournament;
        this.phone = phone;
        this.attended = new LinkedHashSet<>(attended);
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPaid() { return paid; }
    public boolean isInTournament() { return !Boolean.FALSE.equals(inTournament); }
    public String getPhone() { return phone == null ? "" : phone; }
    public Set<String> getAttended() { return attended; }

    public void setProfile(String battingHand, String bowlingArm, String bowlingStyle, boolean keeper, boolean captain,
                           boolean viceCaptain, String jersey) {
        this.battingHand = battingHand;
        this.bowlingArm = bowlingArm;
        this.bowlingStyle = bowlingStyle;
        this.keeper = keeper;
        this.captain = captain;
        this.viceCaptain = viceCaptain;
        this.jersey = jersey;
    }

    public String getBattingHand() { return battingHand == null ? "" : battingHand; }
    public String getBowlingArm() { return bowlingArm == null ? "" : bowlingArm; }
    public String getBowlingStyle() { return bowlingStyle == null ? "" : bowlingStyle; }
    public boolean isKeeper() { return Boolean.TRUE.equals(keeper); }
    public boolean isCaptain() { return Boolean.TRUE.equals(captain); }
    public boolean isViceCaptain() { return Boolean.TRUE.equals(viceCaptain); }
    public String getJerseyName() { return jerseyName == null ? "" : jerseyName; }
    public void setJerseyName(String v) { this.jerseyName = v; }
    public String getProfileSavedAt() { return profileSavedAt == null ? "" : profileSavedAt; }
    public void setProfileSavedAt(String v) { this.profileSavedAt = v; }
    public String getJersey() { return jersey == null ? "" : jersey; }
}

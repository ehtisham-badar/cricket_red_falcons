package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/**
 * A season-wide charge the admin puts on chosen players only (a shirt order, say): each of them is charged
 * {@code amount}. It isn't tied to a day, and shows as its own column on the season sheet.
 */
@Entity
@Table(name = "member_charges")
public class MemberCharge {

    @Id
    @Column(length = 64)
    private String id;

    private int position;

    @Column(nullable = false, length = 80)
    private String title = "";

    /** What each chosen player is charged. */
    @Column(nullable = false)
    private String amount = "";

    /** Comma-separated ids of the players charged. */
    @Column(name = "player_ids", nullable = false, length = 2000)
    private String playerIds = "";

    /** Comma-separated ids of the players on it who have sent the money (a tracker; it doesn't change balances). */
    @Column(name = "received_ids", length = 2000)
    private String receivedIds = "";

    protected MemberCharge() {
    }

    public MemberCharge(String id, int position, String title, String amount, String playerIds, String receivedIds) {
        this.id = id;
        this.position = position;
        this.title = title;
        this.amount = amount;
        this.playerIds = playerIds;
        this.receivedIds = receivedIds;
    }

    public String getId() { return id; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getAmount() { return amount; }
    public String getPlayerIds() { return playerIds == null ? "" : playerIds; }
    public String getReceivedIds() { return receivedIds == null ? "" : receivedIds; }
}

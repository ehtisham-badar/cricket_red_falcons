package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A player's profile picture, already shrunk by the app. */
@Entity
@Table(name = "player_photos")
public class PlayerPhoto {

    @Id
    @Column(name = "player_id", length = 64)
    private String playerId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false, length = 400000)
    private byte[] data;

    @Column(name = "updated_at", nullable = false, length = 40)
    private String updatedAt;

    protected PlayerPhoto() {
    }

    public PlayerPhoto(String playerId, String contentType, byte[] data, String updatedAt) {
        this.playerId = playerId;
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getPlayerId() { return playerId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public String getUpdatedAt() { return updatedAt; }
}

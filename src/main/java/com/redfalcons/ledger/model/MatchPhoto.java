package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A photo of a match, already shrunk by the app. */
@Entity
@Table(name = "match_photos")
public class MatchPhoto {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false, length = 600000)
    private byte[] data;

    /** "admin", a manager's id or a player's id, so people can remove their own. */
    @Column(name = "uploader_id", nullable = false, length = 64)
    private String uploaderId;

    @Column(name = "uploader_name", nullable = false, length = 80)
    private String uploaderName;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    protected MatchPhoto() {
    }

    public MatchPhoto(String id, String matchId, String contentType, byte[] data, String uploaderId, String uploaderName, String createdAt) {
        this.id = id;
        this.matchId = matchId;
        this.contentType = contentType;
        this.data = data;
        this.uploaderId = uploaderId;
        this.uploaderName = uploaderName;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMatchId() { return matchId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public String getUploaderId() { return uploaderId; }
    public String getUploaderName() { return uploaderName; }
    public String getCreatedAt() { return createdAt; }
}

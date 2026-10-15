package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** A quick question for the team; the choices are kept one per line. */
@Entity
@Table(name = "polls")
public class Poll {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "question", length = 120)
    private String question;

    @Column(name = "choices", length = 400)
    private String choices;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "author", length = 80)
    private String author;

    @Column(name = "match_id", length = 64)
    private String matchId;

    @Column(name = "created_at", length = 40)
    private String createdAt;

    @Column(name = "closed")
    private boolean closed;

    protected Poll() {
    }

    public Poll(String id, String question, String choices, String authorId, String author, String matchId, String createdAt, boolean closed) {
        this.id = id;
        this.question = question;
        this.choices = choices;
        this.authorId = authorId;
        this.author = author;
        this.matchId = matchId;
        this.createdAt = createdAt;
        this.closed = closed;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public String getChoices() { return choices; }
    public String getAuthorId() { return authorId; }
    public String getAuthor() { return author; }
    public String getMatchId() { return matchId; }
    public String getCreatedAt() { return createdAt; }
    public boolean isClosed() { return closed; }
}

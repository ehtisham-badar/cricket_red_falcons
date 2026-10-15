package com.redfalcons.ledger.model;

import jakarta.persistence.*;

/** Someone's answer to a poll; voterKey is a player id, a manager id or "admin". */
@Entity
@Table(name = "poll_votes")
public class PollVote {

    @Id
    @Column(name = "id", length = 120)
    private String id;

    @Column(name = "poll_id", length = 32)
    private String pollId;

    @Column(name = "voter_key", length = 64)
    private String voterKey;

    @Column(name = "voter_name", length = 80)
    private String voterName;

    @Column(name = "choice")
    private int choice;

    protected PollVote() {
    }

    public PollVote(String id, String pollId, String voterKey, String voterName, int choice) {
        this.id = id;
        this.pollId = pollId;
        this.voterKey = voterKey;
        this.voterName = voterName;
        this.choice = choice;
    }

    public String getId() { return id; }
    public String getPollId() { return pollId; }
    public String getVoterKey() { return voterKey; }
    public String getVoterName() { return voterName; }
    public int getChoice() { return choice; }
}

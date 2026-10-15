package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.MatchNote;
import com.redfalcons.ledger.repo.*;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Removes votes, predictions and notes of matches that no longer exist. */
@Component
public class EngageCleanup {

    private final MatchVoteRepository votes;
    private final MomClosedRepository closed;
    private final PredictionRepository predictions;
    private final MatchNoteRepository notes;

    public EngageCleanup(MatchVoteRepository votes, MomClosedRepository closed, PredictionRepository predictions, MatchNoteRepository notes) {
        this.votes = votes;
        this.closed = closed;
        this.predictions = predictions;
        this.notes = notes;
    }

    public void matchesRemoved(Set<String> keep) {
        votes.findAll().stream().map(v -> v.getMatchId()).distinct().filter(id -> !keep.contains(id)).forEach(votes::deleteByMatchId);
        predictions.findAll().stream().map(p -> p.getMatchId()).distinct().filter(id -> !keep.contains(id)).forEach(predictions::deleteByMatchId);
        closed.findAll().stream().filter(c -> !keep.contains(c.getMatchId())).forEach(closed::delete);
        notes.findAll().stream().map(MatchNote::getMatchId).filter(id -> !keep.contains(id)).forEach(notes::deleteById);
    }
}

package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollVoteRepository extends JpaRepository<PollVote, String> {
    List<PollVote> findByPollId(String pollId);

    void deleteByPollId(String pollId);
}

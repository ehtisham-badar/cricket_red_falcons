package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MatchVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchVoteRepository extends JpaRepository<MatchVote, String> {
    List<MatchVote> findByMatchId(String matchId);

    void deleteByMatchId(String matchId);
}

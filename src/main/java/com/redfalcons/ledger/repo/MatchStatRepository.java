package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MatchStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchStatRepository extends JpaRepository<MatchStat, String> {
}

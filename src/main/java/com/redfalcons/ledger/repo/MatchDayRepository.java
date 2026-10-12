package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchDayRepository extends JpaRepository<MatchDay, String> {
}

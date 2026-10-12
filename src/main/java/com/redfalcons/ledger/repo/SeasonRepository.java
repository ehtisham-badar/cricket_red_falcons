package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, String> {
}

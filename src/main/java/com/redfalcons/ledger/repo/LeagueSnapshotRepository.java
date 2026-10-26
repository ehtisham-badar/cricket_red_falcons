package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.LeagueSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSnapshotRepository extends JpaRepository<LeagueSnapshot, String> {
}

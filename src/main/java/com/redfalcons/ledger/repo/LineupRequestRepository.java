package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.LineupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineupRequestRepository extends JpaRepository<LineupRequest, String> {

    List<LineupRequest> findByStatusOrderByCreatedAtAsc(String status);

    List<LineupRequest> findTop20ByManagerIdOrderByCreatedAtDesc(String managerId);

    List<LineupRequest> findByManagerIdAndMatchIdAndStatus(String managerId, String matchId, String status);

    void deleteByManagerId(String managerId);
}

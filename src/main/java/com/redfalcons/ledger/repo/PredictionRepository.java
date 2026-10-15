package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, String> {
    List<Prediction> findByMatchId(String matchId);

    void deleteByMatchId(String matchId);
}

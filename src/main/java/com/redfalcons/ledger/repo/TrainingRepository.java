package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Training;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingRepository extends JpaRepository<Training, String> {
}

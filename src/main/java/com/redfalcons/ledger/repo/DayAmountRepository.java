package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.DayAmount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DayAmountRepository extends JpaRepository<DayAmount, String> {
}

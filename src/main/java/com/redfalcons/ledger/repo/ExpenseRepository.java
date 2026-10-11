package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, String> {
}

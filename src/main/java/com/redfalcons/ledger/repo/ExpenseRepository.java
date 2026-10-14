package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, String> {

    /** Ids and amounts only, so no entities get loaded (a bulk delete follows in a save). */
    @Query("select e.id, e.amount from Expense e")
    List<Object[]> idsAndAmounts();
}

package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.TournamentPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentPaymentRepository extends JpaRepository<TournamentPayment, String> {
}

package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
}

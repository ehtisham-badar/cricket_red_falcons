package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.ShirtPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShirtPaymentRepository extends JpaRepository<ShirtPayment, String> {
}

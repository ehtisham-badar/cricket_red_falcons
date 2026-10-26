package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MemberCharge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberChargeRepository extends JpaRepository<MemberCharge, String> {
}

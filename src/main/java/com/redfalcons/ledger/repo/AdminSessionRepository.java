package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
}

package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.ManagerSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagerSessionRepository extends JpaRepository<ManagerSession, String> {

    void deleteByManagerId(String managerId);
}

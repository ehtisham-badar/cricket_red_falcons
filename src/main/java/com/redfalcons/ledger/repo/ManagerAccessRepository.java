package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.ManagerAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagerAccessRepository extends JpaRepository<ManagerAccess, String> {

    List<ManagerAccess> findAllByOrderByCreatedAtAsc();

    long countByStatus(String status);

    Optional<ManagerAccess> findByRequestHash(String requestHash);
}

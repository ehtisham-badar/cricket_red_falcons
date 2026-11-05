package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, String> {
    List<PasswordResetRequest> findByStatus(String status);

    List<PasswordResetRequest> findByUserIdAndStatus(String userId, String status);
}

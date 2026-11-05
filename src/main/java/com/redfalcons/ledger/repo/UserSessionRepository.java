package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUserId(String userId);
}

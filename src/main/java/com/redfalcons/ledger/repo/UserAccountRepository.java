package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPlayerId(String playerId);
}

package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Passkey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeyRepository extends JpaRepository<Passkey, String> {
    List<Passkey> findByUserId(String userId);
}

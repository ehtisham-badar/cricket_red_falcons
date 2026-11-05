package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginEventRepository extends JpaRepository<LoginEvent, String> {
    List<LoginEvent> findByUserIdOrderByAtDesc(String userId);
}

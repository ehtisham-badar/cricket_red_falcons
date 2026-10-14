package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, String> {

    List<AppNotification> findTop30ByOrderByCreatedAtDesc();

    Optional<AppNotification> findByRefKey(String refKey);
}

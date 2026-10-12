package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, String> {
}

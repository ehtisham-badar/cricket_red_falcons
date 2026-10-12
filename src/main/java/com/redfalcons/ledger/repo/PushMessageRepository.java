package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.PushMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PushMessageRepository extends JpaRepository<PushMessage, String> {
    List<PushMessage> findBySubscriptionIdAndDeliveredFalseOrderByCreatedAt(String subscriptionId);

    @Transactional
    void deleteBySubscriptionId(String subscriptionId);
}

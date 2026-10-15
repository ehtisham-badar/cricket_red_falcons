package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollRepository extends JpaRepository<Poll, String> {
}

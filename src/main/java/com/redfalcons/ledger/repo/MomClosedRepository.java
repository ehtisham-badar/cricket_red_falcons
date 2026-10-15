package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MomClosed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MomClosedRepository extends JpaRepository<MomClosed, String> {
}

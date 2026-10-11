package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, String> {
}

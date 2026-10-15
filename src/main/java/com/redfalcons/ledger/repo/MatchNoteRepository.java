package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MatchNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchNoteRepository extends JpaRepository<MatchNote, String> {
}

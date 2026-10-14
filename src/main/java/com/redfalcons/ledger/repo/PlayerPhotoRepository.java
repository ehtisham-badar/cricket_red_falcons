package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.PlayerPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlayerPhotoRepository extends JpaRepository<PlayerPhoto, String> {

    /** Player ids and when their photo changed, without loading the pictures. */
    @Query("select p.playerId, p.updatedAt from PlayerPhoto p")
    List<Object[]> versions();
}

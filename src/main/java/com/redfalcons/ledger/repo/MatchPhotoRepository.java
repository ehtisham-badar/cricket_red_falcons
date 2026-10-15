package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.MatchPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchPhotoRepository extends JpaRepository<MatchPhoto, String> {

    /** Photo details without loading the pictures: id, match, uploader id, uploader name, time. Newest first. */
    @Query("select p.id, p.matchId, p.uploaderId, p.uploaderName, p.createdAt from MatchPhoto p order by p.createdAt desc")
    List<Object[]> all();

    @Query("select distinct p.matchId from MatchPhoto p")
    List<String> matchIds();

    long countByMatchId(String matchId);

    @Modifying
    void deleteByMatchId(String matchId);
}

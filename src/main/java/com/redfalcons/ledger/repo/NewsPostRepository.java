package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.NewsPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsPostRepository extends JpaRepository<NewsPost, String> {
}

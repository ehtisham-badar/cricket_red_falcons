package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}

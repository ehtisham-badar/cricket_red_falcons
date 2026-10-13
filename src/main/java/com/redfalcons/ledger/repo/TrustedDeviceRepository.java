package com.redfalcons.ledger.repo;

import com.redfalcons.ledger.model.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, String> {
}

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.AdminSessionRepository;
import com.redfalcons.ledger.repo.ManagerSessionRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time sign-out of everyone, for the switch to player accounts (5 Nov 2026): every admin, manager and
 * player session ends, so each person signs in again the new way. Data, accounts and the admin's trusted
 * browsers (two-factor) stay. A setting records that it ran, so later restarts leave sessions alone.
 */
@Service
public class SignOutEveryone {

    static final String DONE = "signOutEveryone-2026-11-05";
    private static final Logger log = LoggerFactory.getLogger(SignOutEveryone.class);

    private final SettingRepository settings;
    private final AdminSessionRepository admins;
    private final ManagerSessionRepository managers;
    private final UserSessionRepository users;

    public SignOutEveryone(SettingRepository settings, AdminSessionRepository admins, ManagerSessionRepository managers, UserSessionRepository users) {
        this.settings = settings;
        this.admins = admins;
        this.managers = managers;
        this.users = users;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void once() {
        if (settings.existsById(DONE)) return;
        long a = admins.count(), m = managers.count(), u = users.count();
        admins.deleteAllInBatch();
        managers.deleteAllInBatch();
        users.deleteAllInBatch();
        settings.save(new Setting(DONE, java.time.Instant.now().toString()));
        log.info("Signed everyone out once for the new sign-in: {} admin, {} manager and {} player sessions ended.", a, m, u);
    }
}

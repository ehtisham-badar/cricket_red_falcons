package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.LineupRequest;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.LineupRequestRepository;
import com.redfalcons.ledger.repo.ManagerAccessRepository;
import com.redfalcons.ledger.repo.ManagerSessionRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time removal of the old managers (7 Nov 2026), who signed in with a phrase of their own: their access
 * and sessions end, and the lineups they had proposed that were still waiting are dropped. From now on a
 * manager is an account the admin gave the manager role. A setting records that it ran, so accounts made
 * managers later are left alone.
 */
@Service
public class RemoveOldManagers {

    static final String DONE = "removeOldManagers-2026-11-07";
    private static final Logger log = LoggerFactory.getLogger(RemoveOldManagers.class);

    private final SettingRepository settings;
    private final ManagerAccessRepository access;
    private final ManagerSessionRepository sessions;
    private final LineupRequestRepository lineups;

    public RemoveOldManagers(SettingRepository settings, ManagerAccessRepository access, ManagerSessionRepository sessions,
                             LineupRequestRepository lineups) {
        this.settings = settings;
        this.access = access;
        this.sessions = sessions;
        this.lineups = lineups;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void once() {
        if (settings.existsById(DONE)) return;
        long m = access.count(), s = sessions.count();
        lineups.deleteAll(lineups.findByStatusOrderByCreatedAtAsc(LineupRequest.PENDING));
        access.deleteAllInBatch();
        sessions.deleteAllInBatch();
        settings.save(new Setting(DONE, java.time.Instant.now().toString()));
        log.info("Removed the old phrase managers once: {} managers and {} sessions ended.", m, s);
    }
}

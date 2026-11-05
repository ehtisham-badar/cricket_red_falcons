package com.redfalcons.ledger.service;

import com.redfalcons.ledger.web.LedgerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Local development helper: on startup, replaces this app's data with a copy of the live ledger,
 * so localhost always shows the latest real data. Enabled only when {@code ledger.sync-from} is set
 * (in the git-ignored config/application.properties), so it never runs on the live server.
 */
@Component
@ConditionalOnExpression("'${ledger.sync-from:}' != ''")
public class LiveSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveSync.class);

    private final LedgerService service;
    private final String source;

    public LiveSync(LedgerService service, @Value("${ledger.sync-from}") String source) {
        this.service = service;
        this.source = source.replaceAll("/+$", "");
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LedgerDto live = RestClient.create(source).get().uri("/api/ledger").retrieve().body(LedgerDto.class);
            if (live == null || live.players() == null) {
                log.warn("Live sync: {} returned no ledger; keeping local data.", source);
                return;
            }
            service.save(live);
            log.info("Live sync: copied {} players from {} into the local database.", live.players().size(), source);
        } catch (Exception e) {
            log.warn("Live sync: could not fetch {} ({}); keeping local data.", source, e.getMessage());
        }
    }
}

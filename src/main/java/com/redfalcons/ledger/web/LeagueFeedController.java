package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.LeagueFeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The HPL points table, results and top players from Crickslab, for everyone. */
@RestController
@RequestMapping("/api/hpl")
public class LeagueFeedController {

    private final LeagueFeedService feed;
    private final AdminAuth auth;

    public LeagueFeedController(LeagueFeedService feed, AdminAuth auth) {
        this.feed = feed;
        this.auth = auth;
    }

    /** The admin's refresh button: fetch the latest from Crickslab now. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can refresh the league."));
        }
        return ResponseEntity.ok(feed.refreshNow());
    }

    @GetMapping
    public Map<String, Object> get() {
        return feed.summary();
    }
}

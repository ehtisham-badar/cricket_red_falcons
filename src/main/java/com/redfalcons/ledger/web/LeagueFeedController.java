package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.LeagueFeedService;
import com.redfalcons.ledger.service.MatchReportNews;
import com.redfalcons.ledger.service.ScorecardImporter;
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
    private final ScorecardImporter scorecards;
    private final MatchReportNews reports;

    public LeagueFeedController(LeagueFeedService feed, AdminAuth auth, ScorecardImporter scorecards, MatchReportNews reports) {
        this.feed = feed;
        this.auth = auth;
        this.scorecards = scorecards;
        this.reports = reports;
    }

    /** The admin's refresh button: fetch the latest from Crickslab now. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token) {
        if (!auth.isValidToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can refresh the league."));
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>(feed.refreshNow());
        // The scorecards of our finished matches that have no stats yet; a problem there never spoils the refresh.
        try {
            out.put("scorecards", scorecards.importMissing());
        } catch (Exception e) {
            out.put("scorecards", java.util.List.of(Map.of("match", "Scorecards", "status", "failed", "message", String.valueOf(e.getMessage()))));
        }
        try {
            reports.postNew(); // a match that has just finished gets its News post now rather than at the next check
        } catch (Exception ignored) { /* the refresh itself worked */ }
        return ResponseEntity.ok(out);
    }

    @GetMapping
    public Map<String, Object> get() {
        return feed.summary();
    }
}

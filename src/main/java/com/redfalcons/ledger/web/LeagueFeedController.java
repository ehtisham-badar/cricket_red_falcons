package com.redfalcons.ledger.web;

import com.redfalcons.ledger.service.LeagueFeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The HPL points table, results and top players from Crickslab, for everyone. */
@RestController
@RequestMapping("/api/hpl")
public class LeagueFeedController {

    private final LeagueFeedService feed;

    public LeagueFeedController(LeagueFeedService feed) {
        this.feed = feed;
    }

    @GetMapping
    public Map<String, Object> get() {
        return feed.summary();
    }
}

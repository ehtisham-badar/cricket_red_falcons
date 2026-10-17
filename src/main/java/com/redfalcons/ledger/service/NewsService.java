package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.NewsPost;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.NewsPostRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** News posts: the admin writes them, everyone reads them. */
@Service
public class NewsService {

    /** Set once the first post (the HPL rules) has been added, so deleting it later doesn't bring it back. */
    private static final String SEEDED = "newsSeeded";

    static final String RULES_TITLE = "HPL – Official Cricket Rules";
    static final String RULES_BODY = """
            Published by the HPL tournament management.
            Match format: 100 balls | 5 balls per over

            1. Match Format: Each innings consists of 100 legal balls, with 5 balls per over (20 overs).
            2. Team Squad: 12 players per team — 11 playing players and 1 substitute.
            3. Powerplay: The first 6 overs (30 legal balls) will be the Powerplay. A maximum of 2 fielders are allowed outside the 30-yard circle.
            4. Bowling Limit: Each bowler can bowl a maximum of 4 overs.
            5. Wide Ball: 1 extra run will be awarded, and the delivery must be bowled again.
            6. No-Ball: 1 extra run will be awarded, with an additional delivery. Free-hit rules apply.
            7. Batting & Fielding: Standard cricket rules will apply for scoring, boundaries, dismissals and fielding restrictions.
            8. Match Result: The team scoring the most runs wins. A Super Over may be played in case of a tie.
            9. Discipline: The umpire’s decision is final. All players must maintain discipline and sportsmanship.
            10. Final Authority: Any dispute will be decided by the HPL Tournament Committee.""";

    private final NewsPostRepository posts;
    private final SettingRepository settings;

    public NewsService(NewsPostRepository posts, SettingRepository settings) {
        this.posts = posts;
        this.settings = settings;
    }

    /** Adds the HPL rules as the first news post, once. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnce() {
        if (settings.existsById(SEEDED)) return;
        if (posts.count() == 0) {
            posts.save(new NewsPost(UUID.randomUUID().toString(), RULES_TITLE, "Rules", RULES_BODY, true, Instant.now().toString()));
        }
        settings.save(new Setting(SEEDED, "true"));
    }

    /** Pinned first, then newest first. */
    @Transactional(readOnly = true)
    public List<NewsPost> list() {
        return posts.findAll().stream()
                .sorted(Comparator.comparing(NewsPost::isPinned).reversed().thenComparing(NewsPost::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public NewsPost create(String title, String tag, String body, boolean pinned) {
        String now = Instant.now().toString();
        return posts.save(new NewsPost(UUID.randomUUID().toString(), clean(title, 120, true), clean(tag, 30, false), clean(body, 8000, true), pinned, now));
    }

    @Transactional
    public Optional<NewsPost> update(String id, String title, String tag, String body, boolean pinned) {
        return posts.findById(id).map(p -> {
            p.edit(clean(title, 120, true), clean(tag, 30, false), clean(body, 8000, true), pinned, Instant.now().toString());
            return p;
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!posts.existsById(id)) return false;
        posts.deleteById(id);
        return true;
    }

    private static String clean(String s, int max, boolean required) {
        String t = s == null ? "" : s.strip();
        if (required && t.isEmpty()) throw new IllegalArgumentException("Add a title and some text.");
        return t.length() > max ? t.substring(0, max) : t;
    }
}

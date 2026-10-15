package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.AppNotification;
import com.redfalcons.ledger.model.Expense;
import com.redfalcons.ledger.repo.AppNotificationRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * In-app notifications for viewers and managers. Today: when the admin records an expense. An expense counts
 * once it has an amount; editing it afterwards updates its notification rather than adding another, and
 * deleting it removes the notification. A save that adds many at once (a backup restore) stays quiet.
 */
@Service
public class NotificationService {

    private static final String EXPENSE = "expense:";
    private static final int BULK_LIMIT = 5;
    private static final int KEEP = 200;
    private static final java.time.Duration MAX_AHEAD = java.time.Duration.ofDays(366);

    private final AppNotificationRepository repo;
    private final PushService push;

    public NotificationService(AppNotificationRepository repo, PushService push) {
        this.repo = repo;
        this.push = push;
    }

    /** What everyone sees: the newest 30 that are due (scheduled ones wait for their time), newest first. */
    @Transactional(readOnly = true)
    public List<AppNotification> latest() {
        Instant now = Instant.now();
        return repo.findAll().stream()
                .filter(n -> !Instant.parse(n.getVisibleAt()).isAfter(now))
                .sorted(Comparator.comparing((AppNotification n) -> Instant.parse(n.getVisibleAt())).reversed())
                .limit(30).toList();
    }

    /** Every announcement, including scheduled ones, newest first (for the people who write them). */
    @Transactional(readOnly = true)
    public List<AppNotification> announcements() {
        return repo.findAll().stream().filter(AppNotification::isAnnouncement)
                .sorted(Comparator.comparing((AppNotification n) -> Instant.parse(n.getCreatedAt())).reversed()).toList();
    }

    /** @param sendAt an ISO instant to hold it back until, or null/blank to show it straight away */
    @Transactional
    public AppNotification announce(String authorId, String author, String title, String body, String matchId, String sendAt) {
        String t = title == null ? "" : title.trim();
        String b = body == null ? "" : body.trim();
        if (b.isEmpty()) throw new IllegalArgumentException("Write a message first.");
        if (b.length() > 300) throw new IllegalArgumentException("Keep the message under 300 characters.");
        if (t.isEmpty()) t = "Announcement";
        if (t.length() > 80) t = t.substring(0, 80);
        Instant now = Instant.now();
        String publish = null;
        if (sendAt != null && !sendAt.isBlank()) {
            Instant at;
            try {
                at = Instant.parse(sendAt.trim());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("That send time isn't valid.");
            }
            if (at.isAfter(now.plus(MAX_AHEAD))) throw new IllegalArgumentException("Pick a time within the next year.");
            if (at.isAfter(now)) publish = at.toString();
        }
        String match = matchId == null ? "" : matchId.trim();
        AppNotification n = repo.save(AppNotification.announcement(AdminAuth.randomToken().substring(0, 16), authorId, author, t, b,
                match.isEmpty() ? null : match, publish, now.toString()));
        prune();
        // Straight away: phones with notifications on hear it now. A scheduled one is pushed when its time comes.
        if (publish == null) push.send(t, b, null);
        return n;
    }

    /** Pushes scheduled announcements whose time has come (run every half minute). */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @Transactional
    public void pushDue() {
        Instant now = Instant.now();
        repo.findAll().stream().filter(AppNotification::awaitingPush)
                .filter(n -> !Instant.parse(n.getVisibleAt()).isAfter(now))
                .forEach(n -> {
                    n.markPushed();
                    push.send(n.getTitle(), n.getBody(), null);
                });
    }

    /** The admin announced a match's Playing XI: a bell notice for everyone, and a push to phones. */
    @Transactional
    public void xiAnnounced(String matchId, String title, String body) {
        repo.findByRefKey("xi:" + matchId).ifPresent(repo::delete);
        repo.flush();
        repo.save(AppNotification.notice(AdminAuth.randomToken().substring(0, 16), "xi:" + matchId, title, body, matchId,
                Instant.now().toString()));
        prune();
        push.send(title, body, null);
    }

    /** The admin may delete any announcement; a manager only their own. */
    @Transactional
    public boolean deleteAnnouncement(String id, String actorId, boolean admin) {
        return repo.findById(id).filter(AppNotification::isAnnouncement)
                .filter(n -> admin || n.getAuthorId().equals(actorId))
                .map(n -> { repo.delete(n); return true; }).orElse(false);
    }

    private void prune() {
        List<AppNotification> all = repo.findAll();
        if (all.size() > KEEP) {
            all.stream().sorted(Comparator.comparing(AppNotification::getCreatedAt)).limit(all.size() - KEEP).forEach(repo::delete);
        }
    }

    /**
     * @param oldAmounts expense id to amount, as stored before this save
     * @param now        the expenses as saved
     * @param dayLabels  day id to "Training 1" / "Match 2"
     * @param players    player id to name
     */
    @Transactional
    public void expensesSaved(Map<String, String> oldAmounts, List<Expense> now, Map<String, String> dayLabels,
                              Map<String, String> players, String currency) {
        Set<String> live = new HashSet<>();
        now.forEach(e -> live.add(e.getId()));
        repo.findAll().stream()
                .filter(n -> n.getRefKey().startsWith(EXPENSE) && !live.contains(n.getRefKey().substring(EXPENSE.length())))
                .forEach(repo::delete);

        List<Expense> fresh = new ArrayList<>();
        for (Expense e : now) {
            if (e.getId().startsWith("legacy-") || amount(e.getAmount()).signum() <= 0) continue;
            Optional<AppNotification> existing = repo.findByRefKey(EXPENSE + e.getId());
            if (existing.isPresent()) {
                existing.get().setText("New expense", text(e, dayLabels, players, currency));
            } else if (amount(oldAmounts.get(e.getId())).signum() <= 0) {
                fresh.add(e);
            }
        }
        if (fresh.size() > BULK_LIMIT) return;
        String at = Instant.now().toString();
        for (Expense e : fresh) {
            repo.save(new AppNotification(AdminAuth.randomToken().substring(0, 16), EXPENSE + e.getId(), "New expense",
                    text(e, dayLabels, players, currency), at));
        }
        List<AppNotification> all = repo.findAll();
        if (all.size() > KEEP) {
            all.stream().sorted(Comparator.comparing(AppNotification::getCreatedAt)).limit(all.size() - KEEP).forEach(repo::delete);
        }
    }

    private static String text(Expense e, Map<String, String> dayLabels, Map<String, String> players, String currency) {
        String item = e.getItem() == null || e.getItem().isBlank() ? "Expense" : e.getItem().trim();
        StringBuilder b = new StringBuilder(item).append(" — ").append(currency == null ? "" : currency).append(e.getAmount().trim());
        String day = dayLabels.get(e.getDayId());
        if (day != null) b.append(" · ").append(day);
        String who = players.get(e.getPaidBy());
        if (who != null && !who.isBlank()) b.append(" · paid by ").append(who);
        String s = b.toString();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private static BigDecimal amount(String v) {
        try {
            return v == null || v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}

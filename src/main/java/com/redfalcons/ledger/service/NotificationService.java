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
    private static final int KEEP = 100;

    private final AppNotificationRepository repo;

    public NotificationService(AppNotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AppNotification> latest() {
        return repo.findTop30ByOrderByCreatedAtDesc();
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

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.UserAccount;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.UserAccountRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Squad players' birthdays, from the date of birth on their account. On the day, from 9 am Pakistan time, the whole
 * team gets a notice and a push to wish them. Guests' birthdays are never announced; the year is never shown.
 */
@Service
public class Birthdays {

    private static final ZoneId PAKISTAN = ZoneId.of("Asia/Karachi");
    private static final LocalTime WISH_FROM = LocalTime.of(9, 0);

    public record Upcoming(String playerId, String name, int month, int day, long inDays) {
    }

    private final UserAccountRepository accounts;
    private final PlayerRepository players;
    private final SettingRepository settings;
    private final NotificationService notifications;
    private final Clock clock;

    public Birthdays(UserAccountRepository accounts, PlayerRepository players, SettingRepository settings,
                     NotificationService notifications, Optional<Clock> clock) {
        this.accounts = accounts;
        this.players = players;
        this.settings = settings;
        this.notifications = notifications;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    private LocalDate today() {
        return ZonedDateTime.now(clock).withZoneSameInstant(PAKISTAN).toLocalDate();
    }

    /** The next birthday on or after {@code from}; 29 February falls on the 28th in other years. */
    static LocalDate nextBirthday(LocalDate dob, LocalDate from) {
        MonthDay md = MonthDay.from(dob);
        LocalDate b = md.isValidYear(from.getYear()) ? md.atYear(from.getYear()) : LocalDate.of(from.getYear(), 2, 28);
        if (b.isBefore(from)) {
            int y = from.getYear() + 1;
            b = md.isValidYear(y) ? md.atYear(y) : LocalDate.of(y, 2, 28);
        }
        return b;
    }

    /** Squad players (accounts linked to a player) whose birthday is within {@code days} days, soonest first. */
    @Transactional(readOnly = true)
    public List<Upcoming> upcoming(int days) {
        LocalDate today = today();
        List<Upcoming> out = new ArrayList<>();
        for (UserAccount a : accounts.findAll()) {
            if (a.getPlayerId().isEmpty() || a.getDob().isEmpty()) continue;
            Optional<Player> p = players.findById(a.getPlayerId());
            if (p.isEmpty()) continue;
            LocalDate next = nextBirthday(LocalDate.parse(a.getDob()), today);
            long in = ChronoUnit.DAYS.between(today, next);
            if (in <= days) out.add(new Upcoming(a.getPlayerId(), p.get().getName(), next.getMonthValue(), next.getDayOfMonth(), in));
        }
        out.sort(Comparator.comparingLong(Upcoming::inDays).thenComparing(Upcoming::name));
        return out;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 15 * 60_000)
    public void scheduled() {
        try {
            wishToday();
        } catch (RuntimeException e) {
            // Try again at the next run.
        }
    }

    @Transactional
    public int wishToday() {
        return wishAt(ZonedDateTime.now(clock));
    }

    /** Wishes each squad player whose birthday it is (from 9 am), once a year. Returns how many were wished. */
    @Transactional
    public int wishAt(ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(PAKISTAN);
        if (local.toLocalTime().isBefore(WISH_FROM) || !local.toLocalDate().equals(today())) return 0;
        LocalDate today = local.toLocalDate();
        int n = 0;
        for (Upcoming b : upcoming(0)) {
            String key = "birthday-" + b.playerId() + "-" + today.getYear();
            if (settings.existsById(key)) continue;
            String first = b.name().replaceAll("\\s*\\(.*\\)", "").trim().split(" ")[0];
            notifications.broadcast("birthday:" + b.playerId() + ":" + today.getYear(), "🎂 Happy birthday, " + first + "!",
                    "It’s " + b.name() + "’s birthday today. Wish them from all the Red Falcons!", "");
            settings.save(new Setting(key, today.toString()));
            n++;
        }
        return n;
    }
}

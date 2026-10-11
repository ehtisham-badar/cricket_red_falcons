package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.DayAmount;
import com.redfalcons.ledger.model.Expense;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.Training;
import com.redfalcons.ledger.repo.DayAmountRepository;
import com.redfalcons.ledger.repo.ExpenseRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TrainingRepository;
import com.redfalcons.ledger.web.LedgerDto;
import com.redfalcons.ledger.web.LedgerDto.ExpenseDto;
import com.redfalcons.ledger.web.LedgerDto.PlayerDto;
import com.redfalcons.ledger.web.LedgerDto.SettingsDto;
import com.redfalcons.ledger.web.LedgerDto.TournamentDto;
import com.redfalcons.ledger.web.LedgerDto.TrainingDto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {

    public static final List<String> MATCH_IDS = List.of("m1", "m2", "m3", "m4", "m5");
    private static final Set<String> CURRENCIES = Set.of("Rs ", "PKR ", "₹", "$", "£");
    private static final Sort BY_POSITION = Sort.by("position");
    private static final String TOURNAMENT_FEE = "tournamentFee";
    /** Item name used when a legacy per-day amount is turned into an expense. */
    private static final String LEGACY_ITEM = "Amount spent";

    private final PlayerRepository players;
    private final TrainingRepository trainings;
    private final DayAmountRepository legacyAmounts;
    private final ExpenseRepository expenses;
    private final SettingRepository settings;

    public LedgerService(PlayerRepository players, TrainingRepository trainings, DayAmountRepository legacyAmounts,
                         ExpenseRepository expenses, SettingRepository settings) {
        this.players = players;
        this.trainings = trainings;
        this.legacyAmounts = legacyAmounts;
        this.expenses = expenses;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LedgerDto load() {
        List<PlayerDto> playerDtos = players.findAll(BY_POSITION).stream()
                .map(p -> {
                    Map<String, Boolean> att = new LinkedHashMap<>();
                    p.getAttended().forEach(d -> att.put(d, true));
                    return new PlayerDto(p.getId(), p.getName(), p.getRole(), p.getPaid(), p.isInTournament(), att);
                })
                .toList();

        List<TrainingDto> trainingDtos = trainings.findAll(BY_POSITION).stream()
                .map(t -> new TrainingDto(t.getId(), t.getDesc()))
                .toList();
        if (trainingDtos.isEmpty()) {
            trainingDtos = List.of(new TrainingDto("t1", ""));
        }

        Map<String, List<ExpenseDto>> expenseMap = new LinkedHashMap<>();
        expenses.findAll(BY_POSITION).forEach(e -> expenseMap
                .computeIfAbsent(e.getDayId(), k -> new ArrayList<>())
                .add(new ExpenseDto(e.getId(), e.getItem(), e.getAmount(), e.getPaidBy())));
        // Data saved before expenses existed: show each day's amount as one fund-paid item.
        legacyAmounts.findAll().forEach(a -> {
            if (!a.getAmount().isEmpty() && !expenseMap.containsKey(a.getDayId())) {
                expenseMap.put(a.getDayId(), List.of(
                        new ExpenseDto("legacy-" + a.getDayId(), LEGACY_ITEM, a.getAmount(), "")));
            }
        });

        Map<String, String> s = new HashMap<>();
        settings.findAll().forEach(x -> s.put(x.getKey(), x.getValue()));
        SettingsDto settingsDto = new SettingsDto(
                s.getOrDefault("currency", "Rs "),
                parseIntOr(s.get("maxPerMatch"), 11),
                Boolean.parseBoolean(s.getOrDefault("roundUp", "false")));

        return new LedgerDto("cricket-ledger", 3, Instant.now().toString(), playerDtos,
                dayTotals(expenseMap), trainingDtos, expenseMap,
                new TournamentDto(s.getOrDefault(TOURNAMENT_FEE, "")), settingsDto);
    }

    /** Replaces the whole ledger with the given state (admin only). */
    @Transactional
    public LedgerDto save(LedgerDto in) {
        if (in == null || in.players() == null) {
            throw new IllegalArgumentException("This file is not a valid ledger save file.");
        }

        List<TrainingDto> trainingIn = in.trainings() == null ? List.of() : in.trainings();
        List<Training> newTrainings = new ArrayList<>();
        Set<String> trainingIds = new HashSet<>();
        for (TrainingDto t : trainingIn) {
            if (t == null || blank(t.id()) || !trainingIds.add(t.id())) continue;
            newTrainings.add(new Training(t.id(), newTrainings.size(), nz(t.desc())));
        }
        if (newTrainings.isEmpty()) {
            newTrainings.add(new Training("t1", 0, ""));
            trainingIds.add("t1");
        }

        Set<String> validDays = new HashSet<>(trainingIds);
        validDays.addAll(MATCH_IDS);

        List<Player> newPlayers = new ArrayList<>();
        Set<String> playerIds = new HashSet<>();
        for (PlayerDto p : in.players()) {
            if (p == null || blank(p.id()) || !playerIds.add(p.id())) continue;
            Set<String> attended = new LinkedHashSet<>();
            if (p.att() != null) {
                p.att().forEach((day, on) -> {
                    if (Boolean.TRUE.equals(on) && validDays.contains(day)) attended.add(day);
                });
            }
            newPlayers.add(new Player(p.id(), newPlayers.size(), nz(p.name()).trim(), nz(p.role()),
                    numeric(p.paid()), !Boolean.FALSE.equals(p.tournament()), attended));
        }

        List<Expense> newExpenses = new ArrayList<>();
        Set<String> expenseIds = new HashSet<>();
        Map<String, List<ExpenseDto>> expenseIn = in.expenses() != null ? in.expenses() : fromAmounts(in.amounts());
        expenseIn.forEach((day, list) -> {
            if (!validDays.contains(day) || list == null) return;
            for (ExpenseDto e : list) {
                if (e == null || blank(e.id()) || !expenseIds.add(e.id())) continue;
                String paidBy = playerIds.contains(nz(e.paidBy())) ? e.paidBy() : "";
                newExpenses.add(new Expense(e.id(), day, newExpenses.size(), nz(e.item()).trim(),
                        numeric(e.amount()), paidBy));
            }
        });

        players.deleteAll(); // not InBatch, so Hibernate also removes the player_attendance rows
        trainings.deleteAllInBatch();
        expenses.deleteAllInBatch();
        legacyAmounts.deleteAllInBatch();
        players.flush();

        trainings.saveAll(newTrainings);
        players.saveAll(newPlayers);
        expenses.saveAll(newExpenses);

        String fee = in.tournament() == null ? "" : numeric(in.tournament().fee());
        settings.save(new Setting(TOURNAMENT_FEE, fee));

        SettingsDto s = in.settings();
        if (s != null) {
            if (s.currency() != null && CURRENCIES.contains(s.currency())) {
                settings.save(new Setting("currency", s.currency()));
            }
            if (s.maxPerMatch() != null && s.maxPerMatch() > 0 && s.maxPerMatch() < 100) {
                settings.save(new Setting("maxPerMatch", s.maxPerMatch().toString()));
            }
            if (s.roundUp() != null) {
                settings.save(new Setting("roundUp", s.roundUp().toString()));
            }
        }
        return load();
    }

    /** Version 2 files only have a total per day; treat each as a single fund-paid expense. */
    private static Map<String, List<ExpenseDto>> fromAmounts(Map<String, String> amounts) {
        Map<String, List<ExpenseDto>> out = new LinkedHashMap<>();
        if (amounts == null) return out;
        amounts.forEach((day, amount) -> {
            if (!numeric(amount).isEmpty()) {
                out.put(day, List.of(new ExpenseDto("legacy-" + day, LEGACY_ITEM, amount, "")));
            }
        });
        return out;
    }

    private static Map<String, String> dayTotals(Map<String, List<ExpenseDto>> expenseMap) {
        Map<String, String> totals = new LinkedHashMap<>();
        expenseMap.forEach((day, list) -> {
            BigDecimal sum = BigDecimal.ZERO;
            for (ExpenseDto e : list) {
                if (!e.amount().isEmpty()) sum = sum.add(new BigDecimal(e.amount()));
            }
            totals.put(day, sum.stripTrailingZeros().toPlainString());
        });
        return totals;
    }

    private static String numeric(String v) {
        if (v == null) return "";
        String t = v.trim();
        if (t.isEmpty()) return "";
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) && d >= 0 ? new BigDecimal(t).toPlainString() : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static int parseIntOr(String v, int fallback) {
        try {
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

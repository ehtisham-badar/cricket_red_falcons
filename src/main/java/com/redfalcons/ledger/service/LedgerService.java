package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.DayAmount;
import com.redfalcons.ledger.model.Expense;
import com.redfalcons.ledger.model.Payment;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.Training;
import com.redfalcons.ledger.model.TournamentPayment;
import com.redfalcons.ledger.repo.DayAmountRepository;
import com.redfalcons.ledger.repo.ExpenseRepository;
import com.redfalcons.ledger.repo.PaymentRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TournamentPaymentRepository;
import com.redfalcons.ledger.repo.TrainingRepository;
import com.redfalcons.ledger.web.LedgerDto;
import com.redfalcons.ledger.web.LedgerDto.ExpenseDto;
import com.redfalcons.ledger.web.LedgerDto.PaymentDto;
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
import java.util.regex.Pattern;

@Service
public class LedgerService {

    public static final List<String> MATCH_IDS = List.of("m1", "m2", "m3", "m4", "m5");
    private static final Set<String> CURRENCIES = Set.of("Rs ", "PKR ", "₹", "$", "£");
    private static final Sort BY_POSITION = Sort.by("position");
    private static final String TOURNAMENT_FEE = "tournamentFee";
    private static final String TOURNAMENT_NAME = "tournamentName";
    /** Item name used when a legacy per-day amount is turned into an expense. */
    private static final String LEGACY_ITEM = "Amount spent";
    /** Note on the payment created from a "paid" total recorded before payments were itemised. */
    private static final String LEGACY_PAYMENT_NOTE = "Paid before payment history";
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final PlayerRepository players;
    private final TrainingRepository trainings;
    private final DayAmountRepository legacyAmounts;
    private final ExpenseRepository expenses;
    private final PaymentRepository payments;
    private final TournamentPaymentRepository tournamentPayments;
    private final SettingRepository settings;

    public LedgerService(PlayerRepository players, TrainingRepository trainings, DayAmountRepository legacyAmounts,
                         ExpenseRepository expenses, PaymentRepository payments,
                         TournamentPaymentRepository tournamentPayments, SettingRepository settings) {
        this.players = players;
        this.trainings = trainings;
        this.legacyAmounts = legacyAmounts;
        this.expenses = expenses;
        this.payments = payments;
        this.tournamentPayments = tournamentPayments;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LedgerDto load() {
        Map<String, List<PaymentDto>> paymentsByPlayer = new HashMap<>();
        payments.findAll(BY_POSITION).forEach(x -> paymentsByPlayer
                .computeIfAbsent(x.getPlayerId(), k -> new ArrayList<>())
                .add(new PaymentDto(x.getId(), x.getDate(), x.getAmount(), x.getMethod(), x.getNote())));

        List<PlayerDto> playerDtos = players.findAll(BY_POSITION).stream()
                .map(p -> {
                    Map<String, Boolean> att = new LinkedHashMap<>();
                    p.getAttended().forEach(d -> att.put(d, true));
                    List<PaymentDto> paid = paymentsByPlayer.getOrDefault(p.getId(), legacyPayments(p.getId(), p.getPaid()));
                    return new PlayerDto(p.getId(), p.getName(), p.getRole(), sum(paid.stream().map(PaymentDto::amount)),
                            p.isInTournament(), att, paid);
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
                new TournamentDto(s.getOrDefault(TOURNAMENT_NAME, ""), s.getOrDefault(TOURNAMENT_FEE, ""),
                        tournamentPayments.findAll(BY_POSITION).stream()
                                .map(x -> new PaymentDto(x.getId(), x.getDate(), x.getAmount(), x.getMethod(), x.getNote()))
                                .toList()),
                settingsDto);
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
        List<Payment> newPayments = new ArrayList<>();
        Set<String> playerIds = new HashSet<>();
        Set<String> paymentIds = new HashSet<>();
        for (PlayerDto p : in.players()) {
            if (p == null || blank(p.id()) || !playerIds.add(p.id())) continue;
            Set<String> attended = new LinkedHashSet<>();
            if (p.att() != null) {
                p.att().forEach((day, on) -> {
                    if (Boolean.TRUE.equals(on) && validDays.contains(day)) attended.add(day);
                });
            }
            List<PaymentDto> paymentIn = p.payments() != null ? p.payments() : legacyPayments(p.id(), numeric(p.paid()));
            List<String> amounts = new ArrayList<>();
            for (PaymentDto x : paymentIn) {
                if (x == null || blank(x.id()) || !paymentIds.add(x.id())) continue;
                newPayments.add(new Payment(x.id(), p.id(), newPayments.size(), isoDate(x.date()), numeric(x.amount()),
                        clip(x.method(), 40), clip(x.note(), 200)));
                amounts.add(numeric(x.amount()));
            }
            newPlayers.add(new Player(p.id(), newPlayers.size(), nz(p.name()).trim(), nz(p.role()),
                    sum(amounts.stream()), !Boolean.FALSE.equals(p.tournament()), attended));
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
        payments.deleteAllInBatch();
        tournamentPayments.deleteAllInBatch();
        legacyAmounts.deleteAllInBatch();
        players.flush();

        trainings.saveAll(newTrainings);
        players.saveAll(newPlayers);
        expenses.saveAll(newExpenses);
        payments.saveAll(newPayments);

        TournamentDto tour = in.tournament();
        settings.save(new Setting(TOURNAMENT_FEE, tour == null ? "" : numeric(tour.fee())));
        settings.save(new Setting(TOURNAMENT_NAME, tour == null ? "" : clip(tour.name(), 80)));
        List<TournamentPayment> sent = new ArrayList<>();
        Set<String> sentIds = new HashSet<>();
        if (tour != null && tour.payments() != null) {
            for (PaymentDto x : tour.payments()) {
                if (x == null || blank(x.id()) || !sentIds.add(x.id())) continue;
                sent.add(new TournamentPayment(x.id(), sent.size(), isoDate(x.date()), numeric(x.amount()),
                        clip(x.method(), 40), clip(x.note(), 200)));
            }
        }
        tournamentPayments.saveAll(sent);

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

    /** A "paid" total from before payments were itemised becomes one undated payment. */
    private static List<PaymentDto> legacyPayments(String playerId, String paid) {
        if (paid == null || paid.isEmpty()) return List.of();
        return List.of(new PaymentDto("legacy-pay-" + playerId, "", paid, "", LEGACY_PAYMENT_NOTE));
    }

    private static String sum(java.util.stream.Stream<String> amounts) {
        BigDecimal total = amounts.filter(a -> a != null && !a.isEmpty()).map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() == 0 ? "" : total.stripTrailingZeros().toPlainString();
    }

    private static String isoDate(String s) {
        return ISO_DATE.matcher(nz(s)).matches() ? s : "";
    }

    private static String clip(String s, int max) {
        String t = nz(s).trim();
        return t.length() > max ? t.substring(0, max) : t;
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

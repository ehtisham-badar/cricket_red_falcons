package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.DayAmount;
import com.redfalcons.ledger.model.Expense;
import com.redfalcons.ledger.model.MatchDay;
import com.redfalcons.ledger.model.MatchStat;
import com.redfalcons.ledger.model.Payment;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.Training;
import com.redfalcons.ledger.model.TournamentPayment;
import com.redfalcons.ledger.repo.DayAmountRepository;
import com.redfalcons.ledger.repo.ExpenseRepository;
import com.redfalcons.ledger.repo.MatchDayRepository;
import com.redfalcons.ledger.repo.MatchStatRepository;
import com.redfalcons.ledger.repo.PaymentRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TournamentPaymentRepository;
import com.redfalcons.ledger.repo.TrainingRepository;
import com.redfalcons.ledger.web.LedgerDto;
import com.redfalcons.ledger.web.LedgerDto.ExpenseDto;
import com.redfalcons.ledger.web.LedgerDto.MatchDto;
import com.redfalcons.ledger.web.LedgerDto.StatDto;
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

    private static final String MATCH_COUNT = "tournamentMatches";
    private static final String VIEWER_ACCESS = "viewerAccess";
    private static final Set<String> VIEWER_ACCESS_VALUES = Set.of("all", "own");
    private static final String STATS_VISIBILITY = "statsVisibility";
    private static final Set<String> STATS_VISIBILITY_VALUES = Set.of("admin", "all");
    private static final String DAYS_VISIBILITY = "daysVisibility";
    private static final int DEFAULT_MATCH_COUNT = 5;
    private static final Set<String> CURRENCIES = Set.of("Rs ", "PKR ", "₹", "$", "£");
    private static final Sort BY_POSITION = Sort.by("position");
    private static final String TOURNAMENT_FEE = "tournamentFee";
    private static final String TOURNAMENT_NAME = "tournamentName";
    private static final String TOURNAMENT_ADVANCE = "tournamentAdvance";
    private static final List<String> PAY_KEYS = List.of("payBank", "payTitle", "payAccount");
    /** 12th men, stored as "m1=playerId;m2=playerId". */
    private static final String TWELFTH = "twelfthMen";
    /** Batting order per match, stored under "battingOrder.m1" etc. as "id,id,id". */
    private static final String BATTING = "battingOrder.";
    /** Longest value the settings table holds. */
    private static final int SETTING_MAX = 255;
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
    private final MatchDayRepository matches;
    private final MatchStatRepository stats;
    /** Largest count a single stat can hold. */
    private static final int STAT_MAX = 999;

    public LedgerService(PlayerRepository players, TrainingRepository trainings, DayAmountRepository legacyAmounts,
                         ExpenseRepository expenses, PaymentRepository payments,
                         TournamentPaymentRepository tournamentPayments, SettingRepository settings,
                         MatchDayRepository matches, MatchStatRepository stats) {
        this.matches = matches;
        this.stats = stats;
        this.players = players;
        this.trainings = trainings;
        this.legacyAmounts = legacyAmounts;
        this.expenses = expenses;
        this.payments = payments;
        this.tournamentPayments = tournamentPayments;
        this.settings = settings;
    }

    /** The ledger as a viewer may see it (see {@link #forViewer}). */
    @Transactional(readOnly = true)
    public LedgerDto load(boolean admin, String viewerId) {
        return forViewer(load(), admin, viewerId);
    }

    /**
     * What a viewer may see of a ledger (the live one or an archived season): never phone numbers; with
     * viewer access "own" only the payments of the player they say they are ({@code viewerId}); stats only
     * when they are shown to everyone. Attendance stays, since everyone's shares depend on it.
     */
    public static LedgerDto forViewer(LedgerDto full, boolean admin, String viewerId) {
        if (admin) return full;
        SettingsDto st = full.settings();
        boolean ownOnly = st != null && "own".equals(st.viewerAccess());
        boolean showStats = st != null && "all".equals(st.statsVisibility());
        List<PlayerDto> ps = full.players() == null ? List.of() : full.players().stream().map(p -> {
            boolean hide = ownOnly && !p.id().equals(viewerId);
            return new PlayerDto(p.id(), p.name(), p.role(), hide ? "" : p.paid(), p.tournament(), p.att(),
                    hide ? List.of() : p.payments(), null);
        }).toList();
        return new LedgerDto(full.app(), full.version(), full.savedAt(), ps, full.amounts(), full.trainings(), full.expenses(),
                full.tournament(), st, full.twelfth(), full.battingOrder(), full.matches(),
                showStats && full.stats() != null ? full.stats() : Map.of());
    }

    /** The full ledger, as the admin sees it. */
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
                            p.isInTournament(), att, paid, p.getPhone());
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
                Boolean.parseBoolean(s.getOrDefault("roundUp", "false")),
                s.get("payBank"), s.get("payTitle"), s.get("payAccount"), s.getOrDefault(VIEWER_ACCESS, "all"),
                s.getOrDefault(STATS_VISIBILITY, "admin"), s.getOrDefault(DAYS_VISIBILITY, "admin"));

        return new LedgerDto("cricket-ledger", 3, Instant.now().toString(), playerDtos,
                dayTotals(expenseMap), trainingDtos, expenseMap,
                new TournamentDto(s.getOrDefault(TOURNAMENT_NAME, ""), s.getOrDefault(TOURNAMENT_FEE, ""),
                        s.getOrDefault(TOURNAMENT_ADVANCE, ""),
                        tournamentPayments.findAll(BY_POSITION).stream()
                                .map(x -> new PaymentDto(x.getId(), x.getDate(), x.getAmount(), x.getMethod(), x.getNote()))
                                .toList(), parseIntOr(s.get(MATCH_COUNT), DEFAULT_MATCH_COUNT)),
                settingsDto, parseTwelfth(s.get(TWELFTH)), parseBatting(s), storedMatches(), storedStats());
    }

    /** Replaces the whole ledger with the given state (admin only). */
    /**
     * Sets who plays (and the 12th man) for the matches named in {@code playing}; nothing else changes.
     * A match missing from {@code twelfth} has its 12th man cleared, and one missing from {@code batting}
     * goes back to the default batting order. Finished matches already charged their
     * XI, so only the admin may change those.
     */
    @Transactional
    public void saveXi(Map<String, List<String>> playing, Map<String, String> twelfth,
                       Map<String, List<String>> batting, boolean admin) {
        if (playing == null || playing.isEmpty()) return;
        Map<String, MatchDay> known = new HashMap<>();
        matches.findAll().forEach(m -> known.put(m.getId(), m));
        int max = parseIntOr(settings.findById("maxPerMatch").map(Setting::getValue).orElse(null), 11);
        List<Player> all = players.findAll();
        Set<String> playerIds = new HashSet<>();
        all.forEach(p -> playerIds.add(p.getId()));

        Map<String, String> twelfthNow = parseTwelfth(settings.findById(TWELFTH).map(Setting::getValue).orElse(null));
        Map<String, String> sent = twelfth == null ? Map.of() : twelfth;
        Map<String, List<String>> order = batting == null ? Map.of() : batting;
        for (Map.Entry<String, List<String>> e : playing.entrySet()) {
            MatchDay m = known.get(e.getKey());
            if (m == null || (m.isFinished() && !admin)) continue;
            Set<String> xi = new LinkedHashSet<>();
            if (e.getValue() != null) e.getValue().stream().filter(playerIds::contains).forEach(xi::add);
            if (xi.size() > max) throw new IllegalArgumentException("A match can have at most " + max + " players.");
            all.forEach(p -> {
                if (xi.contains(p.getId())) p.getAttended().add(m.getId());
                else p.getAttended().remove(m.getId());
            });
            // A batting order lists only players in the XI, each once; none means the default order.
            String batOrder = String.join(",", order.getOrDefault(m.getId(), List.of()).stream().distinct().filter(xi::contains).toList());
            if (!batOrder.isEmpty() && batOrder.length() <= SETTING_MAX) settings.save(new Setting(BATTING + m.getId(), batOrder));
            else settings.deleteById(BATTING + m.getId());
            String t = sent.get(m.getId());
            if (t != null && playerIds.contains(t) && !xi.contains(t)) twelfthNow.put(m.getId(), t);
            else twelfthNow.remove(m.getId());
        }
        StringJoiner out = new StringJoiner(";");
        twelfthNow.forEach((match, id) -> out.add(match + "=" + id));
        if (out.length() <= SETTING_MAX) settings.save(new Setting(TWELFTH, out.toString()));
    }

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

        // Without matches (an older app), keep the stored ones.
        List<MatchDto> matchIn = in.matches() != null ? in.matches() : storedMatches();
        List<MatchDay> newMatches = new ArrayList<>();
        Set<String> matchIds = new LinkedHashSet<>();
        for (MatchDto m : matchIn) {
            if (m == null || blank(m.id()) || trainingIds.contains(m.id()) || !matchIds.add(m.id())) continue;
            newMatches.add(new MatchDay(m.id(), newMatches.size(), isoDate(m.date()), clip(m.opponent(), 80), clip(m.venue(), 80),
                    Boolean.TRUE.equals(m.finished())));
        }

        Set<String> validDays = new HashSet<>(trainingIds);
        validDays.addAll(matchIds);

        // Without twelfth (an older app or backup file), keep the stored 12th men.
        Map<String, String> twelfthIn = in.twelfth() != null ? in.twelfth()
                : parseTwelfth(settings.findById(TWELFTH).map(Setting::getValue).orElse(null));
        Map<String, String> stored = new HashMap<>();
        settings.findAll().forEach(x -> stored.put(x.getKey(), x.getValue()));
        Map<String, List<String>> battingIn = in.battingOrder() != null ? in.battingOrder() : parseBatting(stored);

        // A save without phone numbers (an older app, or a backup file) keeps the stored ones.
        Map<String, String> storedPhones = new HashMap<>();
        players.findAll().forEach(p -> storedPhones.put(p.getId(), p.getPhone()));

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
            String phone = p.phone() != null ? clip(p.phone(), 20) : storedPhones.getOrDefault(p.id(), "");
            newPlayers.add(new Player(p.id(), newPlayers.size(), nz(p.name()).trim(), nz(p.role()),
                    sum(amounts.stream()), !Boolean.FALSE.equals(p.tournament()), phone, attended));
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
        // Matches may already be loaded (kept when the save has none), so remove only dropped ones
        // and update the rest; a bulk delete would leave stale copies that "update" missing rows.
        matches.deleteAll(matches.findAll().stream().filter(m -> !matchIds.contains(m.getId())).toList());
        expenses.deleteAllInBatch();
        payments.deleteAllInBatch();
        tournamentPayments.deleteAllInBatch();
        legacyAmounts.deleteAllInBatch();
        players.flush();

        trainings.saveAll(newTrainings);
        matches.saveAll(newMatches);
        players.saveAll(newPlayers);
        expenses.saveAll(newExpenses);
        payments.saveAll(newPayments);

        TournamentDto tour = in.tournament();
        settings.save(new Setting(TOURNAMENT_FEE, tour == null ? "" : numeric(tour.fee())));
        settings.save(new Setting(TOURNAMENT_NAME, tour == null ? "" : clip(tour.name(), 80)));
        settings.save(new Setting(TOURNAMENT_ADVANCE, tour == null ? "" : numeric(tour.advance())));
        if (tour != null && tour.matchCount() != null && tour.matchCount() > 0 && tour.matchCount() <= 50) {
            settings.save(new Setting(MATCH_COUNT, tour.matchCount().toString()));
        }
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

        // A 12th man must be a known player who isn't also playing that match.
        Map<String, Set<String>> attendedBy = new HashMap<>();
        newPlayers.forEach(p -> attendedBy.put(p.getId(), p.getAttended()));
        StringJoiner twelfth = new StringJoiner(";");
        twelfthIn.forEach((match, playerId) -> {
            Set<String> att = attendedBy.get(playerId);
            if (matchIds.contains(match) && att != null && !att.contains(match)) twelfth.add(match + "=" + playerId);
        });
        if (twelfth.length() <= SETTING_MAX) settings.save(new Setting(TWELFTH, twelfth.toString()));

        // Stats for known matches and players; without stats (an older app), keep the stored ones.
        Map<String, Map<String, StatDto>> statsIn = in.stats() != null ? in.stats() : storedStats();
        Map<String, MatchStat> newStats = new LinkedHashMap<>();
        statsIn.forEach((match, byPlayer) -> {
            if (!matchIds.contains(match) || byPlayer == null) return;
            byPlayer.forEach((playerId, st) -> {
                if (st == null || !playerIds.contains(playerId)) return;
                int[] n = {
                        count(st.balls()), count(st.ones()), count(st.twos()), count(st.threes()), count(st.fours()), count(st.sixes()),
                        count(st.bowlBalls()), count(st.bowlRuns()), count(st.wickets()),
                        count(st.catches()), count(st.drops()), count(st.misfields()), count(st.goodFielding()),
                        count(st.wides()), count(st.noBalls())};
                boolean out = Boolean.TRUE.equals(st.out());
                if (out || Arrays.stream(n).anyMatch(v -> v > 0)) {
                    MatchStat row = new MatchStat(match, playerId, n, out);
                    newStats.put(row.getId(), row);
                }
            });
        });
        // Remove dropped rows and update the rest (no bulk delete: stored rows may already be loaded).
        stats.deleteAll(stats.findAll().stream().filter(x -> !newStats.containsKey(x.getId())).toList());
        stats.saveAll(newStats.values());

        // A batting order only lists players who are playing that match, each once.
        Set<String> battingKeys = new HashSet<>();
        for (String match : matchIds) {
            List<String> ids = battingIn.get(match);
            String value = ids == null ? "" : String.join(",", ids.stream().distinct()
                    .filter(id -> attendedBy.containsKey(id) && attendedBy.get(id).contains(match)).toList());
            if (!value.isEmpty() && value.length() <= SETTING_MAX) {
                settings.save(new Setting(BATTING + match, value));
                battingKeys.add(BATTING + match);
            }
        }
        settings.deleteAll(settings.findAll().stream()
                .filter(x -> x.getKey().startsWith(BATTING) && !battingKeys.contains(x.getKey())).toList());

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
            if (s.statsVisibility() != null && STATS_VISIBILITY_VALUES.contains(s.statsVisibility())) {
                settings.save(new Setting(STATS_VISIBILITY, s.statsVisibility()));
            }
            if (s.daysVisibility() != null && STATS_VISIBILITY_VALUES.contains(s.daysVisibility())) {
                settings.save(new Setting(DAYS_VISIBILITY, s.daysVisibility()));
            }
            if (s.viewerAccess() != null && VIEWER_ACCESS_VALUES.contains(s.viewerAccess())) {
                settings.save(new Setting(VIEWER_ACCESS, s.viewerAccess()));
            }
            List<String> pay = Arrays.asList(s.payBank(), s.payTitle(), s.payAccount());
            for (int i = 0; i < PAY_KEYS.size(); i++) {
                if (pay.get(i) != null) settings.save(new Setting(PAY_KEYS.get(i), clip(pay.get(i), 60)));
            }
        }
        return load();
    }

    private static Map<String, List<String>> parseBatting(Map<String, String> settingsByKey) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        settingsByKey.forEach((key, v) -> {
            if (key.startsWith(BATTING) && v != null && !v.isEmpty()) out.put(key.substring(BATTING.length()), List.of(v.split(",")));
        });
        return out;
    }

    private Map<String, Map<String, StatDto>> storedStats() {
        Map<String, Map<String, StatDto>> out = new LinkedHashMap<>();
        stats.findAll().forEach(x -> {
            int[] n = x.numbers();
            out.computeIfAbsent(x.getMatchId(), k -> new LinkedHashMap<>()).put(x.getPlayerId(),
                    new StatDto(n[0], n[1], n[2], n[3], n[4], n[5], x.isOut(), n[6], n[7], n[8], n[9], n[10], n[11], n[12], n[13], n[14]));
        });
        return out;
    }

    private static int count(Integer v) {
        return v == null ? 0 : Math.max(0, Math.min(STAT_MAX, v));
    }

    private List<MatchDto> storedMatches() {
        return matches.findAll(BY_POSITION).stream()
                .map(m -> new MatchDto(m.getId(), m.getDate(), m.getOpponent(), m.getVenue(), m.isFinished())).toList();
    }

    private static Map<String, String> parseTwelfth(String stored) {
        Map<String, String> out = new LinkedHashMap<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String pair : stored.split(";")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isEmpty() && !kv[1].isEmpty()) out.put(kv[0], kv[1]);
        }
        return out;
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

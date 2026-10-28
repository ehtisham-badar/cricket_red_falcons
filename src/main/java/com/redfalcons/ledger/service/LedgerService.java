package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.DayAmount;
import com.redfalcons.ledger.model.Expense;
import com.redfalcons.ledger.model.MatchDay;
import com.redfalcons.ledger.model.MatchStat;
import com.redfalcons.ledger.model.MemberCharge;
import com.redfalcons.ledger.model.Payment;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.Training;
import com.redfalcons.ledger.model.TournamentPayment;
import com.redfalcons.ledger.repo.DayAmountRepository;
import com.redfalcons.ledger.repo.ExpenseRepository;
import com.redfalcons.ledger.repo.MatchDayRepository;
import com.redfalcons.ledger.repo.MatchPhotoRepository;
import com.redfalcons.ledger.repo.PlayerPhotoRepository;
import com.redfalcons.ledger.repo.MatchStatRepository;
import com.redfalcons.ledger.repo.MemberChargeRepository;
import com.redfalcons.ledger.repo.PaymentRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TournamentPaymentRepository;
import com.redfalcons.ledger.repo.TrainingRepository;
import com.redfalcons.ledger.web.LedgerDto;
import com.redfalcons.ledger.web.LedgerDto.ChargeDto;
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
    private static final String SHOW_NEXT_MATCH = "showNextMatch";
    private static final String SHOW_FIXTURES = "showFixtures";
    private static final String ADMIN_SHOW_FIXTURES = "adminShowFixtures";
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
    private final NotificationService notifications;
    private final PlayerPhotoRepository photos;
    private final MatchPhotoRepository matchPhotos;
    private final EngageCleanup engage;
    private final MemberChargeRepository charges;
    /** Largest count a single stat can hold. */
    private static final int STAT_MAX = 999;

    public LedgerService(PlayerRepository players, TrainingRepository trainings, DayAmountRepository legacyAmounts,
                         ExpenseRepository expenses, PaymentRepository payments,
                         TournamentPaymentRepository tournamentPayments, SettingRepository settings,
                         MatchDayRepository matches, MatchStatRepository stats, NotificationService notifications,
                         PlayerPhotoRepository photos, MatchPhotoRepository matchPhotos, EngageCleanup engage,
                         MemberChargeRepository charges) {
        this.engage = engage;
        this.charges = charges;
        this.matchPhotos = matchPhotos;
        this.notifications = notifications;
        this.photos = photos;
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
                    hide ? List.of() : p.payments(), null, p.battingHand(), p.bowlingArm(), p.bowlingStyle(),
                    p.keeper(), p.captain(), p.viceCaptain(), p.jersey(), p.photoAt(), p.jerseyName(),
                    // Who has updated their profile is for the admin; players only learn about their own.
                    p.id().equals(viewerId) ? p.profileAt() : "",
                    // Shirt sizes and whether someone has answered are for the admin; players see only their own.
                    p.id().equals(viewerId) ? p.shirtSize() : "", p.id().equals(viewerId) ? p.kitAt() : "");
        }).toList();
        // Who has sent money for an order is payment news too: with "own", a viewer only learns about themselves.
        List<ChargeDto> cs = full.charges() == null ? null : full.charges().stream().map(c -> !ownOnly || c.received() == null ? c
                : new ChargeDto(c.id(), c.title(), c.amount(), c.players(), c.received().stream().filter(id -> id.equals(viewerId)).toList())).toList();
        return new LedgerDto(full.app(), full.version(), full.savedAt(), ps, full.amounts(), full.trainings(), full.expenses(),
                full.tournament(), st, full.twelfth(), full.battingOrder(), full.matches(),
                showStats && full.stats() != null ? full.stats() : Map.of(), cs);
    }

    /** The full ledger, as the admin sees it. */
    @Transactional(readOnly = true)
    public LedgerDto load() {
        Map<String, List<PaymentDto>> paymentsByPlayer = new HashMap<>();
        payments.findAll(BY_POSITION).forEach(x -> paymentsByPlayer
                .computeIfAbsent(x.getPlayerId(), k -> new ArrayList<>())
                .add(new PaymentDto(x.getId(), x.getDate(), x.getAmount(), x.getMethod(), x.getNote(), x.isRefund() ? true : null)));

        Map<String, String> photoAt = new HashMap<>();
        photos.versions().forEach(row -> photoAt.put((String) row[0], (String) row[1]));
        List<PlayerDto> playerDtos = players.findAll(BY_POSITION).stream()
                .map(p -> {
                    Map<String, Boolean> att = new LinkedHashMap<>();
                    p.getAttended().forEach(d -> att.put(d, true));
                    List<PaymentDto> paid = paymentsByPlayer.getOrDefault(p.getId(), legacyPayments(p.getId(), p.getPaid()));
                    return new PlayerDto(p.getId(), p.getName(), p.getRole(), sum(paid.stream().map(LedgerService::signedAmount)),
                            p.isInTournament(), att, paid, p.getPhone(), p.getBattingHand(), p.getBowlingArm(),
                            p.getBowlingStyle(), p.isKeeper(), p.isCaptain(), p.isViceCaptain(), p.getJersey(),
                            photoAt.getOrDefault(p.getId(), ""), p.getJerseyName(), p.getProfileSavedAt(),
                            p.getShirtSize(), p.getKitSavedAt());
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
                .add(new ExpenseDto(e.getId(), e.getItem(), e.getAmount(), e.getPaidBy(),
                        e.getSharedBy().isEmpty() ? List.of() : List.of(e.getSharedBy().split(",")))));
        // Data saved before expenses existed: show each day's amount as one fund-paid item.
        legacyAmounts.findAll().forEach(a -> {
            if (!a.getAmount().isEmpty() && !expenseMap.containsKey(a.getDayId())) {
                expenseMap.put(a.getDayId(), List.of(
                        new ExpenseDto("legacy-" + a.getDayId(), LEGACY_ITEM, a.getAmount(), "", List.of())));
            }
        });

        Map<String, String> s = new HashMap<>();
        settings.findAll().forEach(x -> s.put(x.getKey(), x.getValue()));
        SettingsDto settingsDto = new SettingsDto(
                s.getOrDefault("currency", "Rs "),
                parseIntOr(s.get("maxPerMatch"), 11),
                Boolean.parseBoolean(s.getOrDefault("roundUp", "false")),
                s.get("payBank"), s.get("payTitle"), s.get("payAccount"), s.getOrDefault(VIEWER_ACCESS, "all"),
                s.getOrDefault(STATS_VISIBILITY, "admin"), s.getOrDefault(DAYS_VISIBILITY, "admin"),
                !"false".equals(s.get(SHOW_NEXT_MATCH)), "true".equals(s.get(SHOW_FIXTURES)), !"false".equals(s.get(ADMIN_SHOW_FIXTURES)));

        return new LedgerDto("cricket-ledger", 3, Instant.now().toString(), playerDtos,
                dayTotals(expenseMap), trainingDtos, expenseMap,
                new TournamentDto(s.getOrDefault(TOURNAMENT_NAME, ""), s.getOrDefault(TOURNAMENT_FEE, ""),
                        s.getOrDefault(TOURNAMENT_ADVANCE, ""),
                        tournamentPayments.findAll(BY_POSITION).stream()
                                .map(x -> new PaymentDto(x.getId(), x.getDate(), x.getAmount(), x.getMethod(), x.getNote(), null))
                                .toList(), parseIntOr(s.get(MATCH_COUNT), DEFAULT_MATCH_COUNT)),
                settingsDto, parseTwelfth(s.get(TWELFTH)), parseBatting(s), matchesWithTwelfth(parseTwelfth(s.get(TWELFTH))), storedStats(),
                storedCharges());
    }

    private List<ChargeDto> storedCharges() {
        return charges.findAll(BY_POSITION).stream()
                .map(c -> new ChargeDto(c.getId(), c.getTitle(), c.getAmount(), idList(c.getPlayerIds()), idList(c.getReceivedIds())))
                .toList();
    }

    /** Bowling styles a player may pick for themselves. */
    public static final Set<String> BOWLING_STYLES =
            Set.of("", "Fast", "Medium-fast", "Medium", "Off spin", "Leg spin", "Left-arm spin");

    /** A player's own cricket details (batting hand, bowling arm and style); the rest of the profile is untouched. */
    @Transactional
    public boolean updateProfile(String playerId, String battingHand, String bowlingArm, String bowlingStyle,
                                 String jerseyNumber, String jerseyName, String fullName, String shirtSize, boolean submit, boolean kit) {
        Player p = players.findById(playerId).orElse(null);
        if (p == null) return false;
        // A field left out keeps what is stored.
        if (battingHand == null) battingHand = p.getBattingHand();
        if (bowlingArm == null) bowlingArm = p.getBowlingArm();
        if (bowlingStyle == null) bowlingStyle = p.getBowlingStyle();
        if (!(HANDS.contains(nz(battingHand)) || nz(battingHand).isEmpty())
                || !(HANDS.contains(nz(bowlingArm)) || nz(bowlingArm).isEmpty())
                || !BOWLING_STYLES.contains(nz(bowlingStyle))) {
            throw new IllegalArgumentException("Pick one of the listed options.");
        }
        p.setProfile(nz(battingHand), nz(bowlingArm), nz(bowlingStyle), p.isKeeper(), p.isCaptain(), p.isViceCaptain(),
                jerseyNumber != null ? jersey(jerseyNumber) : p.getJersey());
        if (jerseyName != null) p.setJerseyName(clip(jerseyName, 24).trim());
        if (shirtSize != null) {
            String sz = shirtSize.trim().toUpperCase();
            if (!SHIRT_SIZES.contains(sz)) throw new IllegalArgumentException("Pick a size from the chart.");
            p.setShirtSize(sz);
        }
        if (kit) {
            p.setKitSavedAt(Instant.now().toString());
            String jn = p.getJerseyName(), no = p.getJersey();
            notifications.adminNotice("Kit details", p.getName() + " chose " + (p.getShirtSize().isEmpty() ? "no size yet" : "size " + p.getShirtSize())
                    + (jn.isEmpty() && no.isEmpty() ? "." : ", jersey " + jn + (no.isEmpty() ? "" : " #" + no) + "."));
        }
        String oldName = p.getName();
        if (fullName != null) {
            String n = fullName.trim().replaceAll("\\s+", " ");
            if (n.isEmpty()) throw new IllegalArgumentException("Your name can't be empty.");
            if (n.length() > 60) throw new IllegalArgumentException("Keep your name under 60 characters.");
            for (Player other : players.findAll()) {
                if (!other.getId().equals(playerId) && other.getName().trim().equalsIgnoreCase(n)) {
                    throw new IllegalArgumentException("Another player in the squad already has that name.");
                }
            }
            p.setName(n);
        }
        if (submit) {
            p.setProfileSavedAt(Instant.now().toString());
            List<String> bits = new ArrayList<>();
            if (photos.existsById(playerId)) bits.add("photo");
            if (!p.getJerseyName().isEmpty() || !p.getJersey().isEmpty()) {
                bits.add("jersey " + (p.getJerseyName().isEmpty() ? "" : p.getJerseyName()) + (p.getJersey().isEmpty() ? "" : " #" + p.getJersey()));
            }
            if (!p.getBattingHand().isEmpty()) bits.add(p.getBattingHand() + "-hand bat");
            if (!p.getBowlingStyle().isEmpty() || !p.getBowlingArm().isEmpty()) {
                bits.add(("bowls " + p.getBowlingArm() + " " + p.getBowlingStyle()).replaceAll("\\s+", " ").trim());
            }
            if (!oldName.equals(p.getName())) bits.add(0, "name changed from " + oldName);
            notifications.adminNotice("Profile updated", p.getName() + " updated their profile" + (bits.isEmpty() ? "." : ": " + String.join(", ", bits) + "."));
        }
        return true;
    }

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
            String t = sent.get(m.getId());
            boolean hasTwelfth = t != null && playerIds.contains(t) && !xi.contains(t);
            if (hasTwelfth) twelfthNow.put(m.getId(), t);
            else twelfthNow.remove(m.getId());
            // A batting order lists only the XI and the 12th man, each once; none means the default order.
            String batOrder = String.join(",", order.getOrDefault(m.getId(), List.of()).stream().distinct()
                    .filter(id -> xi.contains(id) || (hasTwelfth && id.equals(t))).toList());
            if (!batOrder.isEmpty() && batOrder.length() <= SETTING_MAX) settings.save(new Setting(BATTING + m.getId(), batOrder));
            else settings.deleteById(BATTING + m.getId());
        }
        StringJoiner out = new StringJoiner(";");
        twelfthNow.forEach((match, id) -> out.add(match + "=" + id));
        if (out.length() <= SETTING_MAX) settings.save(new Setting(TWELFTH, out.toString()));
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

        // Without matches (an older app), keep the stored ones.
        List<MatchDto> matchIn = in.matches() != null ? in.matches() : storedMatches();
        Map<String, Boolean> storedTest = new HashMap<>();
        Map<String, String> storedTime = new HashMap<>();
        Map<String, Boolean> storedAnnounced = new HashMap<>();
        Map<String, String> storedResult = new HashMap<>();
        Map<String, String> storedExtras = new HashMap<>();
        Map<String, Boolean> storedShares = new HashMap<>();
        matches.findAll().forEach(m -> {
            storedTest.put(m.getId(), m.isTest());
            storedAnnounced.put(m.getId(), m.isAnnounced());
            storedTime.put(m.getId(), m.getTime());
            storedResult.put(m.getId(), m.getResult());
            storedExtras.put(m.getId(), m.getOurExtras());
            storedShares.put(m.getId(), m.isTwelfthShares());
        });
        List<MatchDay> newMatches = new ArrayList<>();
        Set<String> matchIds = new LinkedHashSet<>();
        for (MatchDto m : matchIn) {
            if (m == null || blank(m.id()) || trainingIds.contains(m.id()) || !matchIds.add(m.id())) continue;
            MatchDay day = new MatchDay(m.id(), newMatches.size(), isoDate(m.date()), clip(m.opponent(), 80), clip(m.venue(), 80),
                    // A test match never charges anyone, so it can't be finished.
                    Boolean.TRUE.equals(m.finished()) && !(m.test() != null ? m.test() : storedTest.getOrDefault(m.id(), false)),
                    m.test() != null ? m.test() : storedTest.getOrDefault(m.id(), false),
                    m.time() != null ? clockTime(m.time()) : storedTime.getOrDefault(m.id(), ""),
                    m.announced() != null ? m.announced() : storedAnnounced.getOrDefault(m.id(), false),
                    m.result() != null ? cleanResult(m.result()) : storedResult.getOrDefault(m.id(), ""));
            day.setOurExtras(m.extras() != null ? cleanExtras(m.extras()) : storedExtras.getOrDefault(m.id(), ""));
            day.setTwelfthShares(m.twelfthShares() != null ? m.twelfthShares() : storedShares.getOrDefault(m.id(), false));
            newMatches.add(day);
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
        Map<String, Player> storedProfiles = new HashMap<>();
        players.findAll().forEach(p -> {
            storedPhones.put(p.getId(), p.getPhone());
            Player copy = new Player(p.getId(), 0, "", "", "", true, "", Set.of());
            copy.setProfile(p.getBattingHand(), p.getBowlingArm(), p.getBowlingStyle(), p.isKeeper(), p.isCaptain(),
                    p.isViceCaptain(), p.getJersey());
            copy.setJerseyName(p.getJerseyName());
            copy.setProfileSavedAt(p.getProfileSavedAt());
            copy.setShirtSize(p.getShirtSize());
            copy.setKitSavedAt(p.getKitSavedAt());
            storedProfiles.put(p.getId(), copy);
        });

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
                boolean refund = Boolean.TRUE.equals(x.refund());
                String amount = numeric(x.amount());
                newPayments.add(new Payment(x.id(), p.id(), newPayments.size(), isoDate(x.date()), amount,
                        clip(x.method(), 40), clip(x.note(), 200), refund));
                // Money paid back counts against what the player has paid.
                amounts.add(refund && !amount.isEmpty() ? "-" + amount : amount);
            }
            String phone = p.phone() != null ? clip(p.phone(), 20) : storedPhones.getOrDefault(p.id(), "");
            Player row = new Player(p.id(), newPlayers.size(), nz(p.name()).trim(), nz(p.role()),
                    sum(amounts.stream()), !Boolean.FALSE.equals(p.tournament()), phone, attended);
            Player kept = storedProfiles.get(p.id());
            if (p.battingHand() != null || kept == null) {
                row.setProfile(pick(p.battingHand(), HANDS), pick(p.bowlingArm(), HANDS), clip(p.bowlingStyle(), 40),
                        Boolean.TRUE.equals(p.keeper()), Boolean.TRUE.equals(p.captain()), Boolean.TRUE.equals(p.viceCaptain()),
                        jersey(p.jersey()));
            } else {
                row.setProfile(kept.getBattingHand(), kept.getBowlingArm(), kept.getBowlingStyle(), kept.isKeeper(),
                        kept.isCaptain(), kept.isViceCaptain(), kept.getJersey());
            }
            row.setJerseyName(p.jerseyName() != null ? clip(p.jerseyName(), 24) : kept != null ? kept.getJerseyName() : "");
            row.setProfileSavedAt(kept != null ? kept.getProfileSavedAt() : "");
            row.setShirtSize(p.shirtSize() != null ? (SHIRT_SIZES.contains(p.shirtSize().trim().toUpperCase()) ? p.shirtSize().trim().toUpperCase() : "")
                    : kept != null ? kept.getShirtSize() : "");
            row.setKitSavedAt(kept != null ? kept.getKitSavedAt() : "");
            newPlayers.add(row);
        }

        List<Expense> newExpenses = new ArrayList<>();
        Set<String> expenseIds = new HashSet<>();
        Map<String, List<ExpenseDto>> expenseIn = in.expenses() != null ? in.expenses() : fromAmounts(in.amounts());
        expenseIn.forEach((day, list) -> {
            if (!validDays.contains(day) || list == null) return;
            for (ExpenseDto e : list) {
                if (e == null || blank(e.id()) || !expenseIds.add(e.id())) continue;
                String paidBy = playerIds.contains(nz(e.paidBy())) ? e.paidBy() : "";
                // Only real players, each once; nobody left means everyone who played shares it.
                String sharedBy = String.join(",", e.sharedBy() == null ? List.of()
                        : e.sharedBy().stream().filter(playerIds::contains).distinct().toList());
                newExpenses.add(new Expense(e.id(), day, newExpenses.size(), nz(e.item()).trim(),
                        numeric(e.amount()), paidBy, sharedBy));
            }
        });

        // Charges on chosen players (a shirt order); without charges (an older app), keep the stored ones.
        List<ChargeDto> chargeIn = in.charges() != null ? in.charges() : storedCharges();
        List<MemberCharge> newCharges = new ArrayList<>();
        Set<String> chargeIds = new HashSet<>();
        for (ChargeDto c : chargeIn) {
            if (c == null || blank(c.id()) || !chargeIds.add(c.id())) continue;
            List<String> on = c.players() == null ? List.of() : c.players().stream().filter(playerIds::contains).distinct().toList();
            // Only players on the charge can have sent money for it.
            String received = String.join(",", c.received() == null ? List.of() : c.received().stream().filter(on::contains).distinct().toList());
            String who = String.join(",", on);
            if (who.length() > 2000) throw new IllegalArgumentException("Too many players on one charge.");
            newCharges.add(new MemberCharge(c.id(), newCharges.size(), clip(c.title(), 80), numeric(c.amount()), who, received));
        }

        Map<String, String> oldExpenseAmounts = new HashMap<>();
        expenses.idsAndAmounts().forEach(row -> oldExpenseAmounts.put((String) row[0], (String) row[1]));

        players.deleteAll(); // not InBatch, so Hibernate also removes the player_attendance rows
        trainings.deleteAllInBatch();
        // Matches may already be loaded (kept when the save has none), so remove only dropped ones
        // and update the rest; a bulk delete would leave stale copies that "update" missing rows.
        matches.deleteAll(matches.findAll().stream().filter(m -> !matchIds.contains(m.getId())).toList());
        expenses.deleteAllInBatch();
        charges.deleteAll(); // not InBatch: the stored rows may be loaded (kept when the save has none)
        payments.deleteAllInBatch();
        tournamentPayments.deleteAllInBatch();
        legacyAmounts.deleteAllInBatch();
        players.flush();

        trainings.saveAll(newTrainings);
        matches.saveAll(newMatches);
        players.saveAll(newPlayers);
        expenses.saveAll(newExpenses);
        charges.saveAll(newCharges);
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
        Map<String, String> twelfthKept = new HashMap<>();
        twelfthIn.forEach((match, playerId) -> {
            Set<String> att = attendedBy.get(playerId);
            if (matchIds.contains(match) && att != null && !att.contains(match)) {
                twelfth.add(match + "=" + playerId);
                twelfthKept.put(match, playerId);
            }
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
                    // How a batter got out only means something for a dismissed batter.
                    String how = st.dismissal() == null ? "" : st.dismissal().trim().toLowerCase();
                    Integer fow = st.fowScore() == null ? null : count(st.fowScore());
                    if (out && DISMISSALS.contains(how)) {
                        row.setHow(how, clip(st.bowler(), 40).trim(), clip(st.fielder(), 40).trim(), fow, clip(st.fowOver(), 8).trim());
                    } else if (out) {
                        row.setHow("", "", "", fow, clip(st.fowOver(), 8).trim());
                    }
                    newStats.put(row.getId(), row);
                }
            });
        });
        // Remove dropped rows and update the rest (no bulk delete: stored rows may already be loaded).
        stats.deleteAll(stats.findAll().stream().filter(x -> !newStats.containsKey(x.getId())).toList());
        stats.saveAll(newStats.values());

        // A batting order only lists players who are playing that match (or its 12th man), each once.
        Set<String> battingKeys = new HashSet<>();
        for (String match : matchIds) {
            List<String> ids = battingIn.get(match);
            String value = ids == null ? "" : String.join(",", ids.stream().distinct()
                    .filter(id -> (attendedBy.containsKey(id) && attendedBy.get(id).contains(match)) || id.equals(twelfthKept.get(match))).toList());
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
            if (s.showNextMatch() != null) settings.save(new Setting(SHOW_NEXT_MATCH, s.showNextMatch().toString()));
            if (s.showFixtures() != null) settings.save(new Setting(SHOW_FIXTURES, s.showFixtures().toString()));
            if (s.adminShowFixtures() != null) settings.save(new Setting(ADMIN_SHOW_FIXTURES, s.adminShowFixtures().toString()));
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
        photos.versions().stream().map(row -> (String) row[0]).filter(id -> !playerIds.contains(id)).forEach(photos::deleteById);

        Map<String, String> dayLabels = new HashMap<>();
        for (int i = 0; i < newTrainings.size(); i++) dayLabels.put(newTrainings.get(i).getId(), "Training " + (i + 1));
        for (int i = 0; i < newMatches.size(); i++) dayLabels.put(newMatches.get(i).getId(), "Match " + (i + 1));
        Map<String, String> playerNames = new HashMap<>();
        newPlayers.forEach(p -> playerNames.put(p.getId(), p.getName()));
        // Photos of matches that no longer exist go too.
        matchPhotos.matchIds().stream().filter(id -> !matchIds.contains(id)).forEach(matchPhotos::deleteByMatchId);
        engage.matchesRemoved(matchIds);
        // A Playing XI announced just now is news for the team (a restore that announces many stays quiet).
        List<String[]> freshXi = new ArrayList<>();
        int real = 0;
        for (MatchDay m : newMatches) {
            if (m.isTest()) continue;
            real++;
            if (m.isAnnounced() && !storedAnnounced.getOrDefault(m.getId(), false)) {
                String when = m.getDate().isEmpty() ? "" : " · " + java.time.LocalDate.parse(m.getDate())
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
                String opp = m.getOpponent().isBlank() ? "" : " v " + m.getOpponent();
                freshXi.add(new String[]{m.getId(), "Playing XI announced", "Match " + real + ": Red Falcons" + (opp.isEmpty() ? "" : opp) + when
                        + ". Open the app to see the team."});
            }
        }
        if (freshXi.size() <= 2) freshXi.forEach(x -> notifications.xiAnnounced(x[0], x[1], x[2]));
        notifications.expensesSaved(oldExpenseAmounts, newExpenses, dayLabels, playerNames,
                settings.findById("currency").map(Setting::getValue).orElse("Rs "));
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
                    new StatDto(n[0], n[1], n[2], n[3], n[4], n[5], x.isOut(), n[6], n[7], n[8], n[9], n[10], n[11], n[12], n[13], n[14],
                            x.getDismissal(), x.getDismissedBy(), x.getFielder(), x.getFowScore(), x.getFowOver()));
        });
        return out;
    }

    private static int count(Integer v) {
        return v == null ? 0 : Math.max(0, Math.min(STAT_MAX, v));
    }

    /** Four counts (byes, leg byes, wides, no-balls) as "b,lb,w,nb", or "" when none or malformed. */
    private static String cleanExtras(String v) {
        String[] parts = (v == null ? "" : v).split(",", -1);
        if (parts.length != 4) return "";
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                n[i] = Math.max(0, Math.min(STAT_MAX, Integer.parseInt(parts[i].trim())));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return n[0] + n[1] + n[2] + n[3] == 0 ? "" : n[0] + "," + n[1] + "," + n[2] + "," + n[3];
    }

    /** The shirt size chart: XS 18×26 … 3XL 24×32 (chest × length). */
    public static final Set<String> SHIRT_SIZES = Set.of("", "XS", "S", "M", "L", "XL", "2XL", "3XL");

    private static final Set<String> DISMISSALS = Set.of("bowled", "caught", "lbw", "runout", "stumped", "hitwicket", "retired", "other");

    private static String cleanResult(String r) {
        String v = r == null ? "" : r.trim().toLowerCase();
        return v.equals("win") || v.equals("loss") || v.equals("tie") ? v : "";
    }

    private List<MatchDto> storedMatches() {
        return matches.findAll(BY_POSITION).stream()
                .map(m -> new MatchDto(m.getId(), m.getDate(), m.getOpponent(), m.getVenue(), m.isFinished(), m.isTest(), m.getTime(),
                        m.isAnnounced(), m.getResult(), null, m.getOurExtras(), m.isTwelfthShares())).toList();
    }

    /**
     * The matches with twelfthPlayed set: the 12th man batted or bowled, so he shares the costs. Sent to everyone,
     * so balances match even when the stats themselves are hidden from viewers.
     */
    private List<MatchDto> matchesWithTwelfth(Map<String, String> twelfth) {
        Map<String, MatchStat> byId = new HashMap<>();
        stats.findAll().forEach(x -> byId.put(x.getId(), x));
        return storedMatches().stream().map(m -> {
            String pid = twelfth.get(m.id());
            MatchStat st = pid == null ? null : byId.get(m.id() + ":" + pid);
            boolean played = false;
            if (st != null) {
                int[] n = st.numbers();
                played = st.isOut() || n[0] > 0 || n[1] + n[2] + n[3] + n[4] + n[5] > 0 || n[6] > 0 || n[13] + n[14] > 0;
            }
            return new MatchDto(m.id(), m.date(), m.opponent(), m.venue(), m.finished(), m.test(), m.time(), m.announced(), m.result(), played, m.extras(), m.twelfthShares());
        }).toList();
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
        return List.of(new PaymentDto("legacy-pay-" + playerId, "", paid, "", LEGACY_PAYMENT_NOTE, null));
    }

    /** A payment's amount, negative when it is money paid back to the player. */
    private static String signedAmount(PaymentDto x) {
        String a = x.amount();
        return Boolean.TRUE.equals(x.refund()) && a != null && !a.isEmpty() ? "-" + a : a;
    }

    private static String sum(java.util.stream.Stream<String> amounts) {
        BigDecimal total = amounts.filter(a -> a != null && !a.isEmpty()).map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() == 0 ? "" : total.stripTrailingZeros().toPlainString();
    }

    private static List<String> idList(String csv) {
        return csv.isEmpty() ? List.of() : List.of(csv.split(","));
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
                out.put(day, List.of(new ExpenseDto("legacy-" + day, LEGACY_ITEM, amount, "", List.of())));
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

    private static final Set<String> HANDS = Set.of("right", "left");
    private static final Pattern CLOCK = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    /** A kick-off time as HH:MM, or "" when it isn't a valid one. */
    private static String clockTime(String v) {
        String t = v == null ? "" : v.trim();
        return CLOCK.matcher(t).matches() ? t : "";
    }

    /** The value if it is one of the allowed ones, else "". */
    private static String pick(String v, Set<String> allowed) {
        return v != null && allowed.contains(v) ? v : "";
    }

    /** A jersey number: up to three digits, or "". */
    private static String jersey(String v) {
        String t = v == null ? "" : v.replaceAll("\\D", "");
        return t.length() > 3 ? t.substring(0, 3) : t;
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

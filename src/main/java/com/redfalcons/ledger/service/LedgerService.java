package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.DayAmount;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.model.Training;
import com.redfalcons.ledger.repo.DayAmountRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.repo.TrainingRepository;
import com.redfalcons.ledger.web.LedgerDto;
import com.redfalcons.ledger.web.LedgerDto.PlayerDto;
import com.redfalcons.ledger.web.LedgerDto.SettingsDto;
import com.redfalcons.ledger.web.LedgerDto.TrainingDto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {

    public static final List<String> MATCH_IDS = List.of("m1", "m2", "m3", "m4", "m5");
    private static final Set<String> CURRENCIES = Set.of("Rs ", "PKR ", "₹", "$", "£");
    private static final Sort BY_POSITION = Sort.by("position");

    private final PlayerRepository players;
    private final TrainingRepository trainings;
    private final DayAmountRepository amounts;
    private final SettingRepository settings;

    public LedgerService(PlayerRepository players, TrainingRepository trainings,
                         DayAmountRepository amounts, SettingRepository settings) {
        this.players = players;
        this.trainings = trainings;
        this.amounts = amounts;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LedgerDto load() {
        List<PlayerDto> playerDtos = players.findAll(BY_POSITION).stream()
                .map(p -> {
                    Map<String, Boolean> att = new LinkedHashMap<>();
                    p.getAttended().forEach(d -> att.put(d, true));
                    return new PlayerDto(p.getId(), p.getName(), p.getRole(), p.getPaid(), att);
                })
                .toList();

        List<TrainingDto> trainingDtos = trainings.findAll(BY_POSITION).stream()
                .map(t -> new TrainingDto(t.getId(), t.getDesc()))
                .toList();
        if (trainingDtos.isEmpty()) {
            trainingDtos = List.of(new TrainingDto("t1", ""));
        }

        Map<String, String> amountMap = new LinkedHashMap<>();
        amounts.findAll().forEach(a -> amountMap.put(a.getDayId(), a.getAmount()));

        Map<String, String> s = new HashMap<>();
        settings.findAll().forEach(x -> s.put(x.getKey(), x.getValue()));
        SettingsDto settingsDto = new SettingsDto(
                s.getOrDefault("currency", "Rs "),
                parseIntOr(s.get("maxPerMatch"), 11),
                Boolean.parseBoolean(s.getOrDefault("roundUp", "false")));

        return new LedgerDto("cricket-ledger", 2, Instant.now().toString(),
                playerDtos, amountMap, trainingDtos, settingsDto);
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
            newPlayers.add(new Player(p.id(), newPlayers.size(), nz(p.name()).trim(),
                    nz(p.role()), numeric(p.paid()), attended));
        }

        List<DayAmount> newAmounts = new ArrayList<>();
        if (in.amounts() != null) {
            in.amounts().forEach((day, amount) -> {
                if (validDays.contains(day)) newAmounts.add(new DayAmount(day, numeric(amount)));
            });
        }

        // deleteAll (not InBatch) so Hibernate also removes the player_attendance rows.
        players.deleteAll();
        trainings.deleteAllInBatch();
        amounts.deleteAllInBatch();
        players.flush();

        trainings.saveAll(newTrainings);
        players.saveAll(newPlayers);
        amounts.saveAll(newAmounts);

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

    private static String numeric(String v) {
        if (v == null) return "";
        String t = v.trim();
        if (t.isEmpty()) return "";
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? t : "";
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

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.HplSquad;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.HplSquadRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The other HPL teams' squads. Crickslab only loads its squads inside its own web page, so ours are a copy
 * (taken from its Squads tab on 17 Oct 2026) that the admin keeps up to date.
 */
@Service
public class SquadService {

    static final int MAX_PLAYERS = 30;
    static final int MAX_LINE = 60;

    /** Set once the squads below have been added, so a squad the admin removes doesn't come back. */
    private static final String SEEDED = "squadsSeeded";

    static final Map<String, List<String>> CRICKSLAB_SQUADS = Map.of(
            "Anggler", List.of("Ibrahim Butt (c)", "Rao Ameer Hamza - All-rounder", "Mahad Rafiq - All-rounder", "Abdul Ahad",
                    "Shariq Munir", "Ibraheem Faheem - Batter", "Sana Ullah", "Abdul Rehman - All-rounder", "Muaaz Bin Jawad",
                    "Shahbakht", "Abdullah Joyia", "Captain Asif", "Mustafa - All-rounder", "Huzaifa", "Babar"),
            "Galacticos", List.of("Fahad Ali (c)", "Zohaib Shoukat - All-rounder", "Moeez Butt", "Hamza Wattoo",
                    "M Huzaifa Jutt - All-rounder", "Ahmad Adnan", "Shaffin Raza - Bowler", "Hashim Khalid - All-rounder", "Ubaid",
                    "Hamza Raheel", "Ali Zer", "Mekaal Gardezi", "Ch Ahmed", "Zeeshan"),
            "Ragerovers", List.of("Abdul Ahad (c) - All-rounder", "Aasim Latif - Batter", "Bilal Akram - Bowler",
                    "Umair Usman - All-rounder", "Haider Khan - All-rounder", "Junaid Khalid - Batter", "Hamza Sultan - All-rounder",
                    "Dawood Ali - All-rounder", "Momin Ali Jutt - All-rounder", "Soban - Bowler", "Farhan - Bowler", "Farhan Khan"),
            "Raptors", List.of("Ali Raza (c) - Batter", "Farhan Imtiaz", "Amraiz - Bowler", "Umair Ahmed - All-rounder",
                    "Abdul Mannan - All-rounder", "Adeel Jafar", "Shahid Kabir - Batter", "Abdullah Shahid - All-rounder",
                    "Taha Khokhar", "Adnan", "Adeel Mughal", "Syed Mohsin Zaidi - Batting all-rounder"));

    private final HplSquadRepository squads;
    private final SettingRepository settings;

    public SquadService(HplSquadRepository squads, SettingRepository settings) {
        this.squads = squads;
        this.settings = settings;
    }

    /** Adds the squads from Crickslab once, without touching any the admin already saved. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnce() {
        if (settings.existsById(SEEDED)) return;
        CRICKSLAB_SQUADS.forEach((team, players) -> {
            if (!squads.existsById(key(team))) save(team, players);
        });
        settings.save(new Setting(SEEDED, "true"));
    }

    /** "RAGEROVERS", "Rage Rovers" and "rage-rovers" all become "ragerovers". */
    public static String key(String team) {
        return team == null ? "" : team.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Transactional(readOnly = true)
    public List<HplSquad> list() {
        return squads.findAll();
    }

    /** Saves a team's squad (blank lines and repeats dropped); an empty list removes it. */
    @Transactional
    public HplSquad save(String team, List<String> players) {
        String key = key(team);
        if (key.isEmpty() || key.length() > 60) throw new IllegalArgumentException("Pick a team.");
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (String p : players == null ? List.<String>of() : players) {
            String line = p == null ? "" : p.strip().replaceAll("\\s+", " ");
            if (line.isEmpty()) continue;
            if (line.length() > MAX_LINE) line = line.substring(0, MAX_LINE).strip();
            if (seen.add(line.toLowerCase(Locale.ROOT))) lines.add(line);
        }
        if (lines.size() > MAX_PLAYERS) throw new IllegalArgumentException("A squad can have at most " + MAX_PLAYERS + " players.");
        String name = team.strip().replaceAll("\\s+", " ");
        if (name.length() > 80) name = name.substring(0, 80);
        String now = Instant.now().toString();
        if (lines.isEmpty()) {
            squads.deleteById(key);
            return new HplSquad(key, name, "", now);
        }
        String joined = String.join("\n", lines);
        final String n = name;
        return squads.findById(key)
                .map(s -> { s.edit(n, joined, now); return s; })
                .orElseGet(() -> squads.save(new HplSquad(key, n, joined, now)));
    }
}

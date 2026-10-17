package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.HplSquad;
import com.redfalcons.ledger.repo.HplSquadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The other HPL teams' squads. Crickslab keeps its squads behind a sign-in, so the admin types them in. */
@Service
public class SquadService {

    static final int MAX_PLAYERS = 30;
    static final int MAX_LINE = 60;

    private final HplSquadRepository squads;

    public SquadService(HplSquadRepository squads) {
        this.squads = squads;
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

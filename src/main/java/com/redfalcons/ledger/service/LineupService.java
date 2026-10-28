package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.LineupRequest;
import com.redfalcons.ledger.model.ManagerAccess;
import com.redfalcons.ledger.model.MatchDay;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.Setting;
import com.redfalcons.ledger.repo.LineupRequestRepository;
import com.redfalcons.ledger.repo.MatchDayRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.SettingRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Lineup requests. A manager proposes the playing XI and 12th man for a match that isn't finished; the
 * ledger only changes when the admin approves. A newer request from the same manager for the same match
 * replaces their pending one.
 */
@Service
public class LineupService {

    private final LineupRequestRepository requests;
    private final MatchDayRepository matches;
    private final PlayerRepository players;
    private final SettingRepository settings;
    private final LedgerService ledger;
    private final PushService push;

    public LineupService(LineupRequestRepository requests, MatchDayRepository matches, PlayerRepository players,
                         SettingRepository settings, LedgerService ledger, PushService push) {
        this.requests = requests;
        this.matches = matches;
        this.players = players;
        this.settings = settings;
        this.ledger = ledger;
        this.push = push;
    }

    @Transactional
    public LineupRequest submit(ManagerAccess manager, String matchId, List<String> playing, String twelfth, List<String> batting) {
        MatchDay match = matches.findById(matchId == null ? "" : matchId).orElse(null);
        if (match == null) throw new IllegalArgumentException("No such match.");
        if (match.isFinished()) throw new IllegalArgumentException("That match is finished, so its lineup is locked.");
        Set<String> known = new HashSet<>();
        players.findAll().forEach(p -> known.add(p.getId()));
        Set<String> xi = new LinkedHashSet<>();
        if (playing != null) playing.stream().filter(known::contains).forEach(xi::add);
        int max = intOr(settings.findById("maxPerMatch").map(Setting::getValue).orElse(null), 11);
        if (xi.size() > max) throw new IllegalArgumentException("A match can have at most " + max + " players.");
        String t = twelfth != null && known.contains(twelfth) && !xi.contains(twelfth) ? twelfth : "";
        String bat = String.join(",", (batting == null ? List.<String>of() : batting).stream().distinct().filter(id -> xi.contains(id) || id.equals(t)).toList());
        requests.deleteAll(requests.findByManagerIdAndMatchIdAndStatus(manager.getId(), match.getId(), LineupRequest.PENDING));
        requests.flush();
        LineupRequest saved = requests.save(new LineupRequest(AdminAuth.randomToken().substring(0, 16), manager.getId(), manager.getName(),
                match.getId(), String.join(",", xi), t, bat, Instant.now().toString()));
        push.sendToAdmins("Lineup proposal", manager.getName() + " proposed the XI for " + labelOf(match.getId())
                + " (" + xi.size() + " playing). Open the app to approve or decline.");
        return saved;
    }

    /** A manager's own recent requests, newest first. */
    @Transactional(readOnly = true)
    public List<LineupRequest> mine(String managerId) {
        return requests.findTop20ByManagerIdOrderByCreatedAtDesc(managerId);
    }

    @Transactional(readOnly = true)
    public List<LineupRequest> pending() {
        return requests.findByStatusOrderByCreatedAtAsc(LineupRequest.PENDING);
    }

    /** Applies the proposed lineup to the ledger. */
    @Transactional
    public boolean approve(String id) {
        LineupRequest r = requests.findById(id).filter(x -> LineupRequest.PENDING.equals(x.getStatus())).orElse(null);
        if (r == null) return false;
        MatchDay match = matches.findById(r.getMatchId()).orElse(null);
        if (match == null || match.isFinished()) {
            throw new IllegalArgumentException("That match is finished or gone. Reopen it first, or decline this request.");
        }
        List<String> xi = r.getPlaying().isEmpty() ? List.of() : Arrays.asList(r.getPlaying().split(","));
        List<String> bat = r.getBatting().isEmpty() ? List.of() : Arrays.asList(r.getBatting().split(","));
        ledger.saveXi(Map.of(r.getMatchId(), xi), r.getTwelfth().isEmpty() ? Map.of() : Map.of(r.getMatchId(), r.getTwelfth()),
                Map.of(r.getMatchId(), bat), true);
        r.decide(LineupRequest.APPROVED, null, Instant.now().toString());
        return true;
    }

    @Transactional
    public boolean decline(String id, String note) {
        LineupRequest r = requests.findById(id).filter(x -> LineupRequest.PENDING.equals(x.getStatus())).orElse(null);
        if (r == null) return false;
        r.decide(LineupRequest.DECLINED, ManagerAccessService.clipNote(note), Instant.now().toString());
        return true;
    }

    /** "Match 2": matches are numbered by their order. */
    private String labelOf(String matchId) {
        int real = 0, test = 0;
        for (MatchDay m : matches.findAll(org.springframework.data.domain.Sort.by("position"))) {
            if (m.isTest()) test++; else real++;
            if (m.getId().equals(matchId)) return m.isTest() ? "Test " + test : "Match " + real;
        }
        return "a match";
    }

    private static int intOr(String v, int fallback) {
        try { return v == null ? fallback : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}

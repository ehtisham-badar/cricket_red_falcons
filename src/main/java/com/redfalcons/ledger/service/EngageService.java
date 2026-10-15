package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.*;
import com.redfalcons.ledger.repo.*;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * What the team does with the matches: voting for man of the match, quick polls, guessing who wins, and the
 * captain's private notes. Players act as the player they said they are; the admin and managers as themselves.
 */
@Service
public class EngageService {

    public record Who(boolean admin, String managerId, String managerName, String playerId, String playerName) {
        public boolean staff() { return admin || managerId != null; }
        /** A stable key for polls: a player id, a manager id or "admin". */
        public String key() { return admin ? "admin" : managerId != null ? managerId : playerId; }
        public String name() { return admin ? "Admin" : managerId != null ? managerName : playerName; }
        public boolean known() { return key() != null; }
    }

    public record MomRow(String matchId, boolean closed, String mine, Map<String, Integer> counts, List<String> winners) {
    }

    public record PollOption(String text, Integer count) {
    }

    public record Voter(String name, String option) {
    }

    public record PollRow(String id, String question, List<PollOption> options, Integer mine, boolean closed, String author,
                          String authorId, String matchId, String createdAt, int total, List<Voter> voters) {
    }

    public record PredRow(String matchId, boolean locked, String mine, Map<String, Integer> counts) {
    }

    public record Standing(String playerId, int points, int correct, int played) {
    }

    public record Engage(List<MomRow> mom, List<PollRow> polls, List<PredRow> predictions, List<Standing> leaderboard, String me) {
    }

    public record Notes(String fieldPlan, String battingPlan, String opposition, String updatedBy, String updatedAt) {
    }

    private final MatchVoteRepository votes;
    private final MomClosedRepository closed;
    private final PollRepository polls;
    private final PollVoteRepository pollVotes;
    private final PredictionRepository predictions;
    private final MatchNoteRepository notes;
    private final MatchDayRepository matches;
    private final PlayerRepository players;
    private final NotificationService notifications;
    private final ZoneId zone;

    public EngageService(MatchVoteRepository votes, MomClosedRepository closed, PollRepository polls, PollVoteRepository pollVotes,
                         PredictionRepository predictions, MatchNoteRepository notes, MatchDayRepository matches,
                         PlayerRepository players, NotificationService notifications,
                         @Value("${ledger.timezone:Asia/Karachi}") String zone) {
        this.votes = votes;
        this.closed = closed;
        this.polls = polls;
        this.pollVotes = pollVotes;
        this.predictions = predictions;
        this.notes = notes;
        this.matches = matches;
        this.players = players;
        this.notifications = notifications;
        this.zone = ZoneId.of(zone);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public Engage view(Who who) {
        List<MatchDay> real = matches.findAll().stream().filter(m -> !m.isTest()).toList();
        Set<String> closedIds = closed.findAll().stream().map(MomClosed::getMatchId).collect(Collectors.toSet());

        List<MomRow> mom = new ArrayList<>();
        for (MatchDay m : real) {
            if (!m.isFinished()) continue;
            List<MatchVote> vs = votes.findByMatchId(m.getId());
            boolean isClosed = closedIds.contains(m.getId());
            String mine = who.playerId() == null ? "" : vs.stream().filter(v -> v.getVoterId().equals(who.playerId()))
                    .map(MatchVote::getCandidateId).findFirst().orElse("");
            Map<String, Integer> counts = new LinkedHashMap<>();
            vs.forEach(v -> counts.merge(v.getCandidateId(), 1, Integer::sum));
            boolean showCounts = isClosed || who.staff() || !mine.isEmpty();
            List<String> winners = new ArrayList<>();
            if (isClosed && !counts.isEmpty()) {
                int top = Collections.max(counts.values());
                counts.forEach((id, n) -> { if (n == top) winners.add(id); });
            }
            mom.add(new MomRow(m.getId(), isClosed, mine, showCounts ? counts : Map.of(), winners));
        }

        List<PollRow> pollRows = new ArrayList<>();
        polls.findAll().stream().sorted(Comparator.comparing(Poll::getCreatedAt).reversed()).forEach(p -> {
            List<String> opts = List.of(p.getChoices().split("\n"));
            List<PollVote> vs = pollVotes.findByPollId(p.getId());
            int[] n = new int[opts.size()];
            vs.forEach(v -> { if (v.getChoice() >= 0 && v.getChoice() < n.length) n[v.getChoice()]++; });
            Integer mine = who.known() ? vs.stream().filter(v -> v.getVoterKey().equals(who.key())).map(PollVote::getChoice).findFirst().orElse(null) : null;
            boolean show = p.isClosed() || who.staff() || mine != null;
            List<PollOption> shown = new ArrayList<>();
            for (int i = 0; i < opts.size(); i++) shown.add(new PollOption(opts.get(i), show ? n[i] : null));
            List<Voter> voters = who.staff() ? vs.stream().map(v -> new Voter(v.getVoterName(), v.getChoice() < opts.size() ? opts.get(v.getChoice()) : "")).toList() : List.of();
            pollRows.add(new PollRow(p.getId(), p.getQuestion(), shown, mine, p.isClosed(), p.getAuthor(), p.getAuthorId(), p.getMatchId(),
                    p.getCreatedAt(), vs.size(), voters));
        });

        List<PredRow> predRows = new ArrayList<>();
        List<Standing> board = new ArrayList<>();
        Map<String, int[]> table = new LinkedHashMap<>();   // player -> {correct, played}
        for (MatchDay m : real) {
            List<Prediction> ps = predictions.findByMatchId(m.getId());
            if (m.isFinished()) {
                String r = m.getResult();
                if (r.equals("win") || r.equals("loss")) {
                    for (Prediction p : ps) {
                        int[] t = table.computeIfAbsent(p.getPlayerId(), k -> new int[2]);
                        t[1]++;
                        if (p.getPick().equals(r)) t[0]++;
                    }
                }
                continue;
            }
            boolean locked = locked(m);
            String mine = who.playerId() == null ? "" : ps.stream().filter(p -> p.getPlayerId().equals(who.playerId()))
                    .map(Prediction::getPick).findFirst().orElse("");
            Map<String, Integer> counts = new LinkedHashMap<>();
            ps.forEach(p -> counts.merge(p.getPick(), 1, Integer::sum));
            predRows.add(new PredRow(m.getId(), locked, mine, locked || who.staff() ? counts : Map.of()));
        }
        table.forEach((id, t) -> board.add(new Standing(id, t[0], t[0], t[1])));
        board.sort(Comparator.comparingInt(Standing::points).reversed().thenComparingInt(Standing::played));
        return new Engage(mom, pollRows, predRows, board, who.playerId() == null ? "" : who.playerId());
    }

    /** Predictions close when the match starts: at kick-off if it has a time, else when match day begins. */
    boolean locked(MatchDay m) {
        if (m.isFinished()) return true;
        if (m.getDate().isEmpty()) return false;
        try {
            LocalDate d = LocalDate.parse(m.getDate());
            LocalTime t = m.getTime().isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(m.getTime());
            return !LocalDateTime.of(d, t).atZone(zone).toInstant().isAfter(Instant.now());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------- man of the match ----------

    @Transactional
    public void vote(Who who, String matchId, String candidateId) {
        if (who.playerId() == null) throw new IllegalArgumentException("Pick your name on Summary first.");
        MatchDay m = matches.findById(matchId).filter(x -> !x.isTest()).orElseThrow(() -> new IllegalArgumentException("No such match."));
        if (!m.isFinished()) throw new IllegalArgumentException("Voting opens once the match is finished.");
        if (closed.existsById(matchId)) throw new IllegalArgumentException("Voting is closed for this match.");
        Player c = players.findById(candidateId == null ? "" : candidateId).orElseThrow(() -> new IllegalArgumentException("No such player."));
        if (!c.getAttended().contains(matchId)) throw new IllegalArgumentException("Vote for someone who played in this match.");
        if (c.getId().equals(who.playerId())) throw new IllegalArgumentException("You can't vote for yourself.");
        votes.save(new MatchVote(matchId + ":" + who.playerId(), matchId, who.playerId(), c.getId(), Instant.now().toString()));
    }

    /** Admin: close or reopen voting. Closing announces the winner. */
    @Transactional
    public void setMomClosed(String matchId, boolean close) {
        MatchDay m = matches.findById(matchId).filter(x -> !x.isTest() && x.isFinished()).orElseThrow(() -> new IllegalArgumentException("No such finished match."));
        if (!close) {
            closed.deleteById(matchId);
            return;
        }
        List<MatchVote> vs = votes.findByMatchId(matchId);
        if (vs.isEmpty()) throw new IllegalArgumentException("Nobody has voted yet.");
        closed.save(new MomClosed(matchId, Instant.now().toString()));
        Map<String, Integer> counts = new LinkedHashMap<>();
        vs.forEach(v -> counts.merge(v.getCandidateId(), 1, Integer::sum));
        int top = Collections.max(counts.values());
        List<String> names = new ArrayList<>();
        counts.forEach((id, n) -> { if (n == top) players.findById(id).ifPresent(p -> names.add(p.getName())); });
        String opp = m.getOpponent().isBlank() ? "" : " vs " + m.getOpponent();
        notifications.broadcast("mom:" + matchId, "Man of the match", String.join(" & ", names) + " — man of the match" + opp + " (" + top
                + (top == 1 ? " vote" : " votes") + ").", matchId);
    }

    // ---------- polls ----------

    @Transactional
    public PollRow createPoll(Who who, String question, List<String> options, String matchId) {
        if (!who.staff()) throw new IllegalArgumentException("Only the admin and managers can ask a poll.");
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) throw new IllegalArgumentException("Write the question first.");
        if (q.length() > 120) throw new IllegalArgumentException("Keep the question under 120 characters.");
        List<String> opts = (options == null ? List.<String>of() : options).stream().map(o -> o == null ? "" : o.trim())
                .filter(o -> !o.isEmpty()).map(o -> o.length() > 40 ? o.substring(0, 40) : o).distinct().limit(5).toList();
        if (opts.size() < 2) throw new IllegalArgumentException("Give at least two different answers.");
        String id = AdminAuth.randomToken().substring(0, 16);
        polls.save(new Poll(id, q, String.join("\n", opts), who.key(), who.name(), matchId == null ? "" : matchId.trim(), Instant.now().toString(), false));
        notifications.broadcast("poll:" + id, "New poll", q, matchId == null || matchId.isBlank() ? null : matchId.trim());
        return null;
    }

    @Transactional
    public void answerPoll(Who who, String pollId, int choice) {
        if (!who.known()) throw new IllegalArgumentException("Pick your name on Summary first.");
        Poll p = polls.findById(pollId).orElseThrow(() -> new IllegalArgumentException("No such poll."));
        if (p.isClosed()) throw new IllegalArgumentException("This poll is closed.");
        int n = p.getChoices().split("\n").length;
        if (choice < 0 || choice >= n) throw new IllegalArgumentException("Not an answer to this poll.");
        pollVotes.save(new PollVote(pollId + ":" + who.key(), pollId, who.key(), who.name(), choice));
    }

    /** The admin may close or delete any poll; a manager only their own. */
    @Transactional
    public boolean managePoll(Who who, String pollId, boolean delete) {
        Optional<Poll> p = polls.findById(pollId).filter(x -> who.admin() || (who.staff() && x.getAuthorId().equals(who.key())));
        if (p.isEmpty()) return false;
        if (delete) {
            pollVotes.deleteByPollId(pollId);
            polls.delete(p.get());
        } else {
            Poll x = p.get();
            polls.save(new Poll(x.getId(), x.getQuestion(), x.getChoices(), x.getAuthorId(), x.getAuthor(), x.getMatchId(), x.getCreatedAt(), !x.isClosed()));
        }
        return true;
    }

    // ---------- predictions ----------

    @Transactional
    public void predict(Who who, String matchId, String pick) {
        if (who.playerId() == null) throw new IllegalArgumentException("Pick your name on Summary first.");
        MatchDay m = matches.findById(matchId).filter(x -> !x.isTest()).orElseThrow(() -> new IllegalArgumentException("No such match."));
        String v = pick == null ? "" : pick.trim().toLowerCase();
        if (!v.equals("win") && !v.equals("loss")) throw new IllegalArgumentException("Pick win or loss.");
        if (locked(m)) throw new IllegalArgumentException("Predictions are closed for this match.");
        predictions.save(new Prediction(matchId + ":" + who.playerId(), matchId, who.playerId(), v, Instant.now().toString()));
    }

    // ---------- captain's notes ----------

    @Transactional(readOnly = true)
    public Notes notes(String matchId) {
        return notes.findById(matchId).map(n -> new Notes(n.getFieldPlan(), n.getBattingPlan(), n.getOpposition(), n.getUpdatedBy(), n.getUpdatedAt()))
                .orElse(new Notes("", "", "", "", ""));
    }

    @Transactional
    public Notes saveNotes(Who who, String matchId, String field, String batting, String opposition) {
        if (!matches.existsById(matchId)) throw new IllegalArgumentException("No such match.");
        MatchNote n = new MatchNote(matchId, clip(field), clip(batting), clip(opposition), who.name(), Instant.now().toString());
        notes.save(n);
        return new Notes(n.getFieldPlan(), n.getBattingPlan(), n.getOpposition(), n.getUpdatedBy(), n.getUpdatedAt());
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 1500 ? t.substring(0, 1500) : t;
    }
}

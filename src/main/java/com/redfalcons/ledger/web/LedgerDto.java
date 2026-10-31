package com.redfalcons.ledger.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Wire format, also used as the backup file. Version 3 adds {@code expenses}, {@code tournament} and
 * per-player {@code payments}; {@code twelfth} maps a match id to its 12th man's player id (not charged
 * for the match, and null on save keeps the stored ones); {@code battingOrder} maps a match id to its XI's
 * player ids in batting order (null on save keeps the stored ones); {@code matches} are the league matches
 * added so far (null on save keeps the stored ones); {@code stats} maps match id -> player id -> that player's
 * stats in the match (admin only; null on save keeps the stored ones); {@code charges} are season-wide charges
 * on chosen players, such as a shirt order (null on save keeps the stored ones); {@code shirtPayments} is the
 * money the admin has sent to the shirt maker, apart from the league's management (null on save keeps them);
 * {@code amounts} (per-day totals) is still written so older readers keep working, and is read
 * as a single expense per day when a file has no {@code expenses}.
 */
public record LedgerDto(
        String app,
        Integer version,
        String savedAt,
        List<PlayerDto> players,
        Map<String, String> amounts,
        List<TrainingDto> trainings,
        Map<String, List<ExpenseDto>> expenses,
        TournamentDto tournament,
        SettingsDto settings,
        Map<String, String> twelfth,
        Map<String, List<String>> battingOrder,
        List<MatchDto> matches,
        Map<String, Map<String, StatDto>> stats,
        List<ChargeDto> charges,
        List<PaymentDto> shirtPayments) {

    /**
     * {@code paid} is the total of {@code payments}; files without payments are read from {@code paid}.
     * {@code phone} is only sent to the admin; null on save keeps the stored number. The profile fields
     * ({@code battingHand} and {@code bowlingArm} are "right", "left" or ""; {@code jersey} is a number) are
     * everyone's to see; a save without {@code battingHand} keeps the stored profile. {@code photoAt} says
     * when the player's photo last changed ("" when none) and is ignored on save. {@code jerseyName} is the name on the jersey (null on save keeps the stored one); {@code profileAt} is when the player last saved their own profile (ignored on save).
     */
    public record PlayerDto(String id, String name, String role, String paid, Boolean tournament,
                            Map<String, Boolean> att, List<PaymentDto> payments, String phone,
                            String battingHand, String bowlingArm, String bowlingStyle,
                            Boolean keeper, Boolean captain, Boolean viceCaptain, String jersey, String photoAt,
                            String jerseyName, String profileAt, String shirtSize, String kitAt) {
    }

    /**
     * A charge on chosen players only (a shirt order): each of {@code players} is charged {@code amount}.
     * {@code received}: those of them who have sent the money (null on save means none).
     */
    public record ChargeDto(String id, String title, String amount, List<String> players, List<String> received) {
    }

    /** {@code refund}: money the admin paid back to the player; left out of the JSON unless true. */
    public record PaymentDto(String id, String date, String amount, String method, String note,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Boolean refund) {
    }

    /** {@code sharedBy}: the players who share this expense; empty means everyone who played. */
    public record ExpenseDto(String id, String item, String amount, String paidBy, List<String> sharedBy) {
    }

    /**
     * {@code payments} are transfers from the admin to the tournament management; {@code advance} is the
     * amount asked per player up front ('' means the money sent so far, split across the players);
     * {@code matchCount} is how many matches the league fee covers (null means 5).
     */
    public record TournamentDto(String name, String fee, String advance, List<PaymentDto> payments, Integer matchCount) {
    }

    /**
     * {@code finished}: the admin has finished the match, so it charges its XI. {@code test}: a generated
     * test match, which never charges anyone (null on save keeps the stored flag). {@code time} is the kick-off
     * as HH:MM, "" while it isn't confirmed (null on save keeps the stored time).
     */
    /** {@code announced}: the admin announced the Playing XI (null on save keeps the stored choice). {@code result}: win, loss, tie or "" (null keeps the stored one). */
    /** {@code twelfthPlayed} (read-only): the 12th man batted or bowled, so he shares the match's costs. */
    public record MatchDto(String id, String date, String opponent, String venue, Boolean finished, Boolean test, String time,
                           Boolean announced, String result, Boolean twelfthPlayed, String extras, Boolean twelfthShares) {
    }

    /**
     * Batting (balls faced and scoring shots), bowling (legal balls, runs off the bat, wickets, and the wides and
     * no-balls given away) and fielding counts.
     */
    public record StatDto(Integer balls, Integer ones, Integer twos, Integer threes, Integer fours, Integer sixes, Boolean out,
                          Integer bowlBalls, Integer bowlRuns, Integer wickets,
                          Integer catches, Integer drops, Integer misfields, Integer goodFielding,
                          Integer wides, Integer noBalls,
                          String dismissal, String bowler, String fielder, Integer fowScore, String fowOver) {
    }

    public record TrainingDto(String id, String desc) {
    }

    /**
     * The pay* fields are the account players send money to; null means never set. {@code viewerAccess}
     * is "all" (viewers see everyone's balances) or "own" (only their own; others' payments aren't sent).
     * {@code statsVisibility} is "admin" (stats are sent to the admin only) or "all".
     * {@code showNextMatch} and {@code showFixtures} say whether the Summary screen shows the Next match card
     * and the fixtures calendar to players (null on save keeps the stored choice); {@code adminShowFixtures}
     * whether the admin sees the fixtures calendar themselves (null means yes).
     * {@code daysVisibility} works the same way for the Days tab (hidden from viewers unless "all").
     */
    public record SettingsDto(String currency, Integer maxPerMatch, Boolean roundUp,
                              String payBank, String payTitle, String payAccount, String viewerAccess,
                              String statsVisibility, String daysVisibility, Boolean showNextMatch, Boolean showFixtures,
                              Boolean adminShowFixtures) {
    }
}

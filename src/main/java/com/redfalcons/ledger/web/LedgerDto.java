package com.redfalcons.ledger.web;

import java.util.List;
import java.util.Map;

/**
 * Wire format, also used as the backup file. Version 3 adds {@code expenses}, {@code tournament} and
 * per-player {@code payments}; {@code twelfth} maps a match id to its 12th man's player id (not charged
 * for the match, and null on save keeps the stored ones); {@code battingOrder} maps a match id to its XI's
 * player ids in batting order (null on save keeps the stored ones); {@code matches} are the league matches
 * added so far (null on save keeps the stored ones); {@code stats} maps match id -> player id -> that player's
 * stats in the match (admin only; null on save keeps the stored ones);
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
        Map<String, Map<String, StatDto>> stats) {

    /**
     * {@code paid} is the total of {@code payments}; files without payments are read from {@code paid}.
     * {@code phone} is only sent to the admin; null on save keeps the stored number. The profile fields
     * ({@code battingHand} and {@code bowlingArm} are "right", "left" or ""; {@code jersey} is a number) are
     * everyone's to see; a save without {@code battingHand} keeps the stored profile. {@code photoAt} says
     * when the player's photo last changed ("" when none) and is ignored on save.
     */
    public record PlayerDto(String id, String name, String role, String paid, Boolean tournament,
                            Map<String, Boolean> att, List<PaymentDto> payments, String phone,
                            String battingHand, String bowlingArm, String bowlingStyle,
                            Boolean keeper, Boolean captain, Boolean viceCaptain, String jersey, String photoAt) {
    }

    public record PaymentDto(String id, String date, String amount, String method, String note) {
    }

    public record ExpenseDto(String id, String item, String amount, String paidBy) {
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
    public record MatchDto(String id, String date, String opponent, String venue, Boolean finished, Boolean test, String time) {
    }

    /**
     * Batting (balls faced and scoring shots), bowling (legal balls, runs off the bat, wickets, and the wides and
     * no-balls given away) and fielding counts.
     */
    public record StatDto(Integer balls, Integer ones, Integer twos, Integer threes, Integer fours, Integer sixes, Boolean out,
                          Integer bowlBalls, Integer bowlRuns, Integer wickets,
                          Integer catches, Integer drops, Integer misfields, Integer goodFielding,
                          Integer wides, Integer noBalls) {
    }

    public record TrainingDto(String id, String desc) {
    }

    /**
     * The pay* fields are the account players send money to; null means never set. {@code viewerAccess}
     * is "all" (viewers see everyone's balances) or "own" (only their own; others' payments aren't sent).
     * {@code statsVisibility} is "admin" (stats are sent to the admin only) or "all".
     * {@code daysVisibility} works the same way for the Days tab (hidden from viewers unless "all").
     */
    public record SettingsDto(String currency, Integer maxPerMatch, Boolean roundUp,
                              String payBank, String payTitle, String payAccount, String viewerAccess,
                              String statsVisibility, String daysVisibility) {
    }
}

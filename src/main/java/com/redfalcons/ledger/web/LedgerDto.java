package com.redfalcons.ledger.web;

import java.util.List;
import java.util.Map;

/**
 * Wire format, also used as the backup file. Version 3 adds {@code expenses}, {@code tournament} and
 * per-player {@code payments}; {@code twelfth} maps a match id to its 12th man's player id (not charged
 * for the match, and null on save keeps the stored ones);
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
        Map<String, String> twelfth) {

    /**
     * {@code paid} is the total of {@code payments}; files without payments are read from {@code paid}.
     * {@code phone} is only sent to the admin; null on save keeps the stored number.
     */
    public record PlayerDto(String id, String name, String role, String paid, Boolean tournament,
                            Map<String, Boolean> att, List<PaymentDto> payments, String phone) {
    }

    public record PaymentDto(String id, String date, String amount, String method, String note) {
    }

    public record ExpenseDto(String id, String item, String amount, String paidBy) {
    }

    /**
     * {@code payments} are transfers from the admin to the tournament management; {@code advance} is the
     * amount asked per player up front ('' means the money sent so far, split across the players).
     */
    public record TournamentDto(String name, String fee, String advance, List<PaymentDto> payments) {
    }

    public record TrainingDto(String id, String desc) {
    }

    /** The pay* fields are the account players send money to; null means never set. */
    public record SettingsDto(String currency, Integer maxPerMatch, Boolean roundUp,
                              String payBank, String payTitle, String payAccount) {
    }
}

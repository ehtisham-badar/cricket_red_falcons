package com.redfalcons.ledger.web;

import java.util.List;
import java.util.Map;

/**
 * Wire format, also used as the backup file. Version 3 adds {@code expenses} and {@code tournament};
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
        SettingsDto settings) {

    public record PlayerDto(String id, String name, String role, String paid, Boolean tournament,
                            Map<String, Boolean> att) {
    }

    public record ExpenseDto(String id, String item, String amount, String paidBy) {
    }

    public record TournamentDto(String fee) {
    }

    public record TrainingDto(String id, String desc) {
    }

    public record SettingsDto(String currency, Integer maxPerMatch, Boolean roundUp) {
    }
}

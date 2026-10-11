package com.redfalcons.ledger.web;

import java.util.List;
import java.util.Map;

/**
 * Wire format. Matches the backup file format from the spec (app, version, savedAt,
 * players, amounts, trainings) plus a {@code settings} block.
 */
public record LedgerDto(
        String app,
        Integer version,
        String savedAt,
        List<PlayerDto> players,
        Map<String, String> amounts,
        List<TrainingDto> trainings,
        SettingsDto settings) {

    public record PlayerDto(String id, String name, String role, String paid, Map<String, Boolean> att) {
    }

    public record TrainingDto(String id, String desc) {
    }

    public record SettingsDto(String currency, Integer maxPerMatch, Boolean roundUp) {
    }
}

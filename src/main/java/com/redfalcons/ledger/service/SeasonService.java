package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.Season;
import com.redfalcons.ledger.repo.SeasonRepository;
import com.redfalcons.ledger.web.LedgerDto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Archived seasons: copies of the whole ledger, viewed read-only later. */
@Service
public class SeasonService {

    /** A season in the archive list (without its data). */
    public record SeasonInfo(String id, String name, String archivedAt) {
    }

    private final SeasonRepository seasons;
    private final LedgerService ledger;
    private final ObjectMapper json;

    public SeasonService(SeasonRepository seasons, LedgerService ledger, ObjectMapper json) {
        this.seasons = seasons;
        this.ledger = ledger;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<SeasonInfo> list() {
        return seasons.findAll(Sort.by(Sort.Direction.DESC, "archivedAt")).stream()
                .map(s -> new SeasonInfo(s.getId(), s.getName(), s.getArchivedAt())).toList();
    }

    /** Saves the current ledger as a season (admin only). */
    @Transactional
    public SeasonInfo archive(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) clean = "Season";
        if (clean.length() > 120) clean = clean.substring(0, 120);
        Season s = new Season(UUID.randomUUID().toString(), clean, Instant.now().toString(),
                json.writeValueAsString(ledger.load()));
        seasons.save(s);
        return new SeasonInfo(s.getId(), s.getName(), s.getArchivedAt());
    }

    /** An archived season as this viewer may see it. */
    @Transactional(readOnly = true)
    public Optional<LedgerDto> get(String id, boolean admin, String viewerId) {
        return seasons.findById(id)
                .map(s -> LedgerService.forViewer(json.readValue(s.getData(), LedgerDto.class), admin, viewerId));
    }

    @Transactional
    public boolean delete(String id) {
        if (!seasons.existsById(id)) return false;
        seasons.deleteById(id);
        return true;
    }
}

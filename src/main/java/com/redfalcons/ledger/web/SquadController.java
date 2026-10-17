package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.HplSquad;
import com.redfalcons.ledger.service.SquadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HPL team squads: everyone can read them; only the admin can change them. */
@RestController
@RequestMapping("/api/hpl/squads")
public class SquadController {

    private final SquadService squads;
    private final AdminAuth auth;

    public SquadController(SquadService squads, AdminAuth auth) {
        this.squads = squads;
        this.auth = auth;
    }

    public record Draft(String team, List<String> players) {
    }

    public record Row(String key, String team, List<String> players, String updatedAt) {
        static Row of(HplSquad s) {
            List<String> players = s.getPlayers().isEmpty() ? List.of() : List.of(s.getPlayers().split("\n"));
            return new Row(s.getKey(), s.getName(), players, s.getUpdatedAt());
        }
    }

    @GetMapping
    public List<Row> list() {
        return squads.list().stream().map(Row::of).toList();
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                  @RequestBody Draft d) {
        if (!auth.isValidToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can change squads."));
        }
        try {
            return ResponseEntity.ok(Row.of(squads.save(d.team(), d.players())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

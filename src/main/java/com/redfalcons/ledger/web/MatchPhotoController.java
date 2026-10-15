package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.MatchPhoto;
import com.redfalcons.ledger.repo.MatchDayRepository;
import com.redfalcons.ledger.repo.MatchPhotoRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.service.ManagerAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The match gallery. Anyone can look; the admin, managers and players (as the player they said they are) can add
 * photos, and take down their own. The admin can remove any. The app shrinks pictures first; the server checks the bytes
 * really are a small JPEG, PNG or WebP.
 */
@RestController
@RequestMapping("/api")
public class MatchPhotoController {

    static final int MAX_BYTES = 500_000;
    static final int MAX_PER_MATCH = 60;

    private final MatchPhotoRepository photos;
    private final MatchDayRepository matches;
    private final PlayerRepository players;
    private final ManagerAccessService managers;
    private final AdminAuth auth;

    public MatchPhotoController(MatchPhotoRepository photos, MatchDayRepository matches, PlayerRepository players,
                                ManagerAccessService managers, AdminAuth auth) {
        this.photos = photos;
        this.matches = matches;
        this.players = players;
        this.managers = managers;
        this.auth = auth;
    }

    public record Item(String id, String matchId, String byId, String by, String createdAt) {
    }

    private record Actor(String id, String name, boolean admin) {
    }

    private Actor actor(String adminToken, String managerToken, String viewer) {
        if (auth.isValidToken(adminToken)) return new Actor("admin", "Admin", true);
        var mgr = managers.find(managerToken);
        if (mgr.isPresent()) return new Actor(mgr.get().getId(), mgr.get().getName(), false);
        if (viewer != null && !viewer.isBlank()) {
            return players.findById(viewer).map(p -> new Actor(p.getId(), p.getName(), false)).orElse(null);
        }
        return null;
    }

    @GetMapping("/match-photos")
    @Transactional(readOnly = true)
    public List<Item> all(@RequestParam(value = "matchId", required = false) String matchId,
                          @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return photos.all().stream()
                .filter(r -> matchId == null || matchId.equals(r[1]))
                .limit(Math.max(1, Math.min(limit, 500)))
                .map(r -> new Item((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4])).toList();
    }

    @GetMapping("/match-photos/{id}")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        return photos.findById(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/matches/{matchId}/photos", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> add(@PathVariable String matchId,
                                 @RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                 @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String managerToken,
                                 @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer,
                                 @RequestBody byte[] body) {
        Actor who = actor(adminToken, managerToken, viewer);
        if (who == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Pick your name on Summary first to add photos."));
        }
        if (!matches.existsById(matchId)) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such match."));
        if (body == null || body.length == 0) return bad("No picture sent.");
        if (body.length > MAX_BYTES) return bad("That picture is too big.");
        String type = PhotoController.typeOf(body);
        if (type == null) return bad("Use a JPEG, PNG or WebP picture.");
        if (photos.countByMatchId(matchId) >= MAX_PER_MATCH) return bad("This match already has " + MAX_PER_MATCH + " photos.");
        String id = AdminAuth.randomToken().substring(0, 16);
        String at = Instant.now().toString();
        photos.save(new MatchPhoto(id, matchId, type, body, who.id(), who.name(), at));
        return ResponseEntity.ok(new Item(id, matchId, who.id(), who.name(), at));
    }

    @DeleteMapping("/match-photos/{id}")
    public ResponseEntity<?> delete(@PathVariable String id,
                                    @RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String adminToken,
                                    @RequestHeader(value = ManagerController.MANAGER_HEADER, required = false) String managerToken,
                                    @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer) {
        Actor who = actor(adminToken, managerToken, viewer);
        var photo = photos.findById(id);
        if (who == null || photo.isEmpty() || !(who.admin() || photo.get().getUploaderId().equals(who.id()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such photo, or it isn't yours to remove."));
        }
        photos.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    private static ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}

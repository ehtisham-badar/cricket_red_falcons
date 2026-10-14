package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.PlayerPhoto;
import com.redfalcons.ledger.repo.PlayerPhotoRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.service.LedgerService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Player pictures and each player's own cricket details. Anyone can see them; the admin may change any, and a viewer (or manager) only their own,
 * as the player they said they are. The app shrinks pictures before sending, and the server checks the bytes
 * really are a small JPEG, PNG or WebP.
 */
@RestController
@RequestMapping("/api/players/{id}")
public class PhotoController {

    static final int MAX_BYTES = 300_000;

    private final PlayerPhotoRepository photos;
    private final PlayerRepository players;
    private final AdminAuth auth;
    private final LedgerService ledger;

    public PhotoController(PlayerPhotoRepository photos, PlayerRepository players, AdminAuth auth, LedgerService ledger) {
        this.ledger = ledger;
        this.photos = photos;
        this.players = players;
        this.auth = auth;
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        return photos.findById(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        // The app asks with ?v=<updatedAt>, so a changed picture always has a new address.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/photo", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> put(@PathVariable String id,
                                 @RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                 @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer,
                                 @RequestBody byte[] body) {
        if (!allowed(id, token, viewer)) return forbidden();
        if (body == null || body.length == 0) return bad("No picture sent.");
        if (body.length > MAX_BYTES) return bad("That picture is too big.");
        String type = typeOf(body);
        if (type == null) return bad("Use a JPEG, PNG or WebP picture.");
        String at = Instant.now().toString();
        photos.save(new PlayerPhoto(id, type, body, at));
        return ResponseEntity.ok(Map.of("photoAt", at));
    }

    @DeleteMapping("/photo")
    public ResponseEntity<?> delete(@PathVariable String id,
                                    @RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer) {
        if (!allowed(id, token, viewer)) return forbidden();
        if (photos.existsById(id)) photos.deleteById(id);
        return ResponseEntity.ok(Map.of("photoAt", ""));
    }

    public record ProfileBody(String battingHand, String bowlingArm, String bowlingStyle) {
    }

    /** A player's own batting hand, bowling arm and style. The admin may set them for anyone. */
    @PutMapping("/profile")
    public ResponseEntity<?> profile(@PathVariable String id,
                                     @RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                     @RequestHeader(value = LedgerController.VIEWER_HEADER, required = false) String viewer,
                                     @RequestBody ProfileBody body) {
        if (!allowed(id, token, viewer)) return forbidden();
        try {
            ledger.updateProfile(id, body == null ? null : body.battingHand(), body == null ? null : body.bowlingArm(),
                    body == null ? null : body.bowlingStyle());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private boolean allowed(String id, String token, String viewer) {
        if (!players.existsById(id)) return false;
        return auth.isValidToken(token) || id.equals(viewer);
    }

    /** The picture's type from its first bytes, or null when it isn't one we accept. */
    static String typeOf(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "You can only change your own picture."));
    }

    private static ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}

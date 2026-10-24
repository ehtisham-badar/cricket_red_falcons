package com.redfalcons.ledger.web;

import com.redfalcons.ledger.model.NewsPost;
import com.redfalcons.ledger.service.NewsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The News tab: everyone can read; only the admin can add, edit or delete. */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService news;
    private final AdminAuth auth;

    public NewsController(NewsService news, AdminAuth auth) {
        this.news = news;
        this.auth = auth;
    }

    public record Draft(String title, String tag, String body, Boolean pinned) {
    }

    public record Row(String id, String title, String tag, String body, boolean pinned, String createdAt, String updatedAt) {
        static Row of(NewsPost p) {
            return new Row(p.getId(), p.getTitle(), p.getTag(), p.getBody(), p.isPinned(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    @GetMapping
    public List<Row> list() {
        return news.list().stream().map(Row::of).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @RequestBody Draft d) {
        if (!auth.isValidToken(token)) return forbidden();
        try {
            return ResponseEntity.ok(Row.of(news.create(d.title(), d.tag(), d.body(), Boolean.TRUE.equals(d.pinned()))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @PathVariable String id, @RequestBody Draft d) {
        if (!auth.isValidToken(token)) return forbidden();
        try {
            return news.update(id, d.title(), d.tag(), d.body(), Boolean.TRUE.equals(d.pinned()))
                    .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Row.of(p)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such news post.")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record Pin(Boolean pinned) {
    }

    /** Pin or unpin a post without marking it edited. */
    @PutMapping("/{id}/pin")
    public ResponseEntity<?> pin(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                 @PathVariable String id, @RequestBody Pin body) {
        if (!auth.isValidToken(token)) return forbidden();
        return news.pin(id, Boolean.TRUE.equals(body.pinned()))
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Row.of(p)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such news post.")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = LedgerController.TOKEN_HEADER, required = false) String token,
                                    @PathVariable String id) {
        if (!auth.isValidToken(token)) return forbidden();
        return news.delete(id) ? ResponseEntity.ok(Map.of("deleted", id))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such news post."));
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the admin can change the news."));
    }
}

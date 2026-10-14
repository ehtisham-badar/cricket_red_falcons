package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.ManagerAccess;
import com.redfalcons.ledger.model.ManagerSession;
import com.redfalcons.ledger.repo.ManagerAccessRepository;
import com.redfalcons.ledger.repo.ManagerSessionRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * App managers. A viewer asks with a phrase of their choice; the admin approves, declines, changes the
 * phrase or revokes the access whenever they like. Typing an approved phrase on the start screen signs the
 * manager in. The server stores only a hash of each session token.
 */
@Service
public class ManagerAccessService {

    /** Most requests waiting at once, so the list can't be flooded. */
    private static final int MAX_PENDING = 20;
    public static final int PHRASE_MIN = 8;
    public static final int PHRASE_MAX = 60;

    public static final String NONE = "none";

    /** A signed-in manager. */
    public record Login(String token, String name) {
    }

    private final ManagerAccessRepository repo;
    private final ManagerSessionRepository sessions;
    private final AdminAuth adminAuth;

    public ManagerAccessService(ManagerAccessRepository repo, ManagerSessionRepository sessions, AdminAuth adminAuth) {
        this.repo = repo;
        this.sessions = sessions;
        this.adminAuth = adminAuth;
    }

    /** A new pending request; returns the secret the browser keeps to check on it. */
    @Transactional
    public String request(String name, String phrase, String label) {
        if (repo.countByStatus(ManagerAccess.PENDING) >= MAX_PENDING) {
            throw new IllegalArgumentException("Too many requests are waiting. Try again later.");
        }
        String clean = checkPhrase(phrase, null);
        String secret = AdminAuth.randomToken();
        repo.save(new ManagerAccess(AdminAuth.randomToken().substring(0, 16), clip(name, "Guest"), clip(label, "Browser"),
                clean, AdminAuth.hash(secret), Instant.now().toString()));
        return secret;
    }

    /** "pending", "approved", or "none" (never asked, declined, or revoked). */
    @Transactional(readOnly = true)
    public String status(String secret) {
        if (secret == null || secret.isBlank()) return NONE;
        return repo.findByRequestHash(AdminAuth.hash(secret)).map(ManagerAccess::getStatus).orElse(NONE);
    }

    /** The requester withdraws their request, or gives up their access. */
    @Transactional
    public void leave(String secret) {
        if (secret == null || secret.isBlank()) return;
        repo.findByRequestHash(AdminAuth.hash(secret)).ifPresent(m -> delete(m.getId()));
    }

    /** Signs in an approved manager by their phrase; empty when no approved manager has it. */
    @Transactional
    public Optional<Login> login(String phrase) {
        if (phrase == null || phrase.isBlank()) return Optional.empty();
        byte[] typed = phrase.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        for (ManagerAccess m : repo.findAll()) {
            boolean same = MessageDigest.isEqual(typed, m.getPhrase().toLowerCase().getBytes(StandardCharsets.UTF_8));
            if (same && ManagerAccess.APPROVED.equals(m.getStatus())) {
                String token = AdminAuth.randomToken();
                sessions.save(new ManagerSession(AdminAuth.hash(token), m.getId(), Instant.now().toString()));
                return Optional.of(new Login(token, m.getName()));
            }
        }
        return Optional.empty();
    }

    /** True while the session's manager is still approved. */
    @Transactional(readOnly = true)
    public boolean isManager(String token) {
        return manager(token).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<String> nameOf(String token) {
        return manager(token).map(ManagerAccess::getName);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteById(AdminAuth.hash(token));
    }

    @Transactional(readOnly = true)
    public List<ManagerAccess> list() {
        return repo.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public boolean approve(String id) {
        Optional<ManagerAccess> m = repo.findById(id);
        m.ifPresent(x -> x.approve(Instant.now().toString()));
        return m.isPresent();
    }

    /** The admin picks a new phrase; the manager's signed-in browsers are signed out. */
    @Transactional
    public boolean changePhrase(String id, String phrase) {
        Optional<ManagerAccess> m = repo.findById(id);
        if (m.isEmpty()) return false;
        m.get().setPhrase(checkPhrase(phrase, id));
        sessions.deleteByManagerId(id);
        return true;
    }

    /** Declines a request or revokes approved access, ending the manager's sessions. */
    @Transactional
    public boolean remove(String id) {
        if (!repo.existsById(id)) return false;
        delete(id);
        return true;
    }

    private void delete(String id) {
        sessions.deleteByManagerId(id);
        repo.deleteById(id);
    }

    private Optional<ManagerAccess> manager(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(AdminAuth.hash(token))
                .flatMap(s -> repo.findById(s.getManagerId()))
                .filter(m -> ManagerAccess.APPROVED.equals(m.getStatus()));
    }

    /** A usable phrase: long enough, not the admin key, and not already someone else's. */
    private String checkPhrase(String phrase, String ownId) {
        String t = phrase == null ? "" : phrase.trim();
        if (t.length() < PHRASE_MIN) throw new IllegalArgumentException("The phrase needs at least " + PHRASE_MIN + " characters.");
        if (t.length() > PHRASE_MAX) throw new IllegalArgumentException("The phrase can be at most " + PHRASE_MAX + " characters.");
        boolean taken = adminAuth.isAdminKey(t) || repo.findAll().stream()
                .anyMatch(m -> !m.getId().equals(ownId) && m.getPhrase().equalsIgnoreCase(t));
        if (taken) throw new IllegalArgumentException("That phrase can't be used. Pick a different one.");
        return t;
    }

    private static String clip(String v, String fallback) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) t = fallback;
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}

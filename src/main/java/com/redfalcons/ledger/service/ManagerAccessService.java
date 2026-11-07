package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.ManagerAccess;
import com.redfalcons.ledger.model.ManagerSession;
import com.redfalcons.ledger.model.UserAccount;
import com.redfalcons.ledger.repo.LineupRequestRepository;
import com.redfalcons.ledger.repo.ManagerSessionRepository;
import com.redfalcons.ledger.repo.UserAccountRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * App managers. A manager is an account the admin gave the "manager" role (Accounts tab). Signing in with that
 * account starts a manager session; taking the role away ends it. The server stores only a hash of each
 * session token. A manager can set the playing XI (the admin approves it), and write announcements, polls,
 * captain's notes and photos.
 */
@Service
public class ManagerAccessService {

    public static final String ROLE = "manager";

    private final UserAccountRepository accounts;
    private final ManagerSessionRepository sessions;
    private final LineupRequestRepository lineups;

    public ManagerAccessService(UserAccountRepository accounts, ManagerSessionRepository sessions, LineupRequestRepository lineups) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.lineups = lineups;
    }

    /** Starts a manager session for an account that has the manager role, and returns its token. */
    @Transactional
    public String startSession(String accountId) {
        String token = AdminAuth.randomToken();
        sessions.save(new ManagerSession(AdminAuth.hash(token), accountId, Instant.now().toString()));
        return token;
    }

    /** True while the session's account is still a manager. */
    @Transactional(readOnly = true)
    public boolean isManager(String token) {
        return manager(token).isPresent();
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteById(AdminAuth.hash(token));
    }

    /** Ends a manager's sessions and a manager's waiting proposals away too (the role was removed or the account deleted). */
    @Transactional
    public void dropAccount(String accountId) {
        sessions.deleteByManagerId(accountId);
        lineups.deleteByManagerId(accountId);
    }

    /** The manager behind a signed-in session, while their account still has the role. */
    @Transactional(readOnly = true)
    public Optional<ManagerAccess> find(String token) {
        return manager(token);
    }

    private Optional<ManagerAccess> manager(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(AdminAuth.hash(token))
                .flatMap(s -> accounts.findById(s.getManagerId()))
                .filter(a -> ROLE.equals(a.getRole()))
                .map(ManagerAccessService::of);
    }

    /** A manager as the rest of the app knows one: the account's id and name. */
    private static ManagerAccess of(UserAccount a) {
        ManagerAccess m = new ManagerAccess(a.getId(), a.getName(), "", "", "", a.getCreatedAt());
        m.approve(a.getCreatedAt());
        return m;
    }

    static String clipNote(String note) {
        String t = note == null ? "" : note.trim();
        return t.isEmpty() ? null : t.length() > 300 ? t.substring(0, 300) : t;
    }
}

package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.LoginEvent;
import com.redfalcons.ledger.model.Passkey;
import com.redfalcons.ledger.model.PasswordResetRequest;
import com.redfalcons.ledger.model.Player;
import com.redfalcons.ledger.model.UserAccount;
import com.redfalcons.ledger.model.UserSession;
import com.redfalcons.ledger.repo.LoginEventRepository;
import com.redfalcons.ledger.repo.PasskeyRepository;
import com.redfalcons.ledger.repo.PasswordResetRequestRepository;
import com.redfalcons.ledger.repo.PlayerRepository;
import com.redfalcons.ledger.repo.UserAccountRepository;
import com.redfalcons.ledger.repo.UserSessionRepository;
import com.redfalcons.ledger.web.AdminAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Player accounts: sign up with email, password and full name; sign in with email and password (or the
 * device's biometrics once turned on). A name that is a squad player's links the account to that player;
 * a near match is only linked once they confirm it. The admin and managers keep signing in as before.
 */
@Service
public class AccountService {

    static final Duration REMEMBER = Duration.ofDays(30);
    static final Duration SHORT = Duration.ofHours(12);
    static final int MAX_FAILS = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    static final int MIN_PASSWORD = 6;
    static final int KEEP_EVENTS = 100;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String TEMP_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository accounts;
    private final UserSessionRepository sessions;
    private final LoginEventRepository events;
    private final PasswordResetRequestRepository resets;
    private final PasskeyRepository passkeys;
    private final PlayerRepository players;
    private final WebAuthnService webAuthn;

    public AccountService(UserAccountRepository accounts, UserSessionRepository sessions, LoginEventRepository events,
                          PasswordResetRequestRepository resets, PasskeyRepository passkeys, PlayerRepository players,
                          WebAuthnService webAuthn) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.events = events;
        this.resets = resets;
        this.passkeys = passkeys;
        this.players = players;
        this.webAuthn = webAuthn;
    }

    /** A signed-in account and its new session token. */
    public record Signed(UserAccount account, String token) {
    }

    /** After sign-up: the account (linked or not), its session, squad players it may be, and whether its exact name was already taken. */
    public record SignedUp(UserAccount account, String token, List<NameMatch.Squad> possible, boolean taken) {
    }

    // ---------- signing up and in ----------

    @Transactional
    public SignedUp signup(String email, String password, String name, boolean remember, String label) {
        String mail = email(email);
        String full = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (full.isEmpty()) throw new IllegalArgumentException("Enter your full name.");
        if (full.length() > 60) throw new IllegalArgumentException("That name is too long.");
        checkPassword(password);
        if (accounts.findByEmail(mail).isPresent()) throw new IllegalArgumentException("There's already an account with this email. Sign in instead.");
        String now = Instant.now().toString();
        UserAccount a = accounts.save(new UserAccount(UUID.randomUUID().toString().replace("-", ""), mail, full, PasswordHasher.hash(password), now));

        NameMatch.Result m = NameMatch.match(full, squad());
        Set<String> claimed = claimed();
        boolean taken = false;
        List<NameMatch.Squad> possible = List.of();
        if (m.exact() != null && !claimed.contains(m.exact().id())) {
            link(a, m.exact());
        } else {
            taken = m.exact() != null;
            possible = m.possible().stream().filter(p -> !claimed.contains(p.id())).toList();
        }
        a.loggedIn(now, "signup");
        record(a.getId(), "signup", label, true);
        return new SignedUp(a, startSession(a, remember, label), possible, taken);
    }

    // A wrong password must still count (and be recorded), so the error doesn't undo the transaction.
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Signed login(String email, String password, boolean remember, String label) {
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> found = accounts.findByEmail(mail);
        if (found.isEmpty()) throw new IllegalArgumentException("Wrong email or password.");
        UserAccount a = found.get();
        Instant now = Instant.now();
        if (!a.getLockedUntil().isEmpty() && Instant.parse(a.getLockedUntil()).isAfter(now)) {
            throw new IllegalArgumentException("Too many wrong passwords. Try again in a few minutes, or use Forgot password.");
        }
        if (!PasswordHasher.matches(password, a.getPasswordHash())) {
            int fails = a.getFailedCount() + 1;
            a.failed(fails >= MAX_FAILS ? 0 : fails, fails >= MAX_FAILS ? now.plus(LOCK).toString() : "");
            record(a.getId(), "password", label, false);
            throw new IllegalArgumentException(fails >= MAX_FAILS
                    ? "Too many wrong passwords. Try again in 15 minutes, or use Forgot password."
                    : "Wrong email or password.");
        }
        a.loggedIn(now.toString(), "password");
        record(a.getId(), "password", label, true);
        return new Signed(a, startSession(a, remember, label));
    }

    /** The account behind a session token, while the session lasts. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(AdminAuth.hash(token))
                .filter(s -> Instant.parse(s.getExpiresAt()).isAfter(Instant.now()))
                .flatMap(s -> accounts.findById(s.getUserId()));
    }

    /** The squad player an account sees the app as: linked, still in the squad, and not waiting for a new password. */
    @Transactional(readOnly = true)
    public Optional<String> playerOf(UserAccount a) {
        if (a == null || a.isMustChange() || a.getPlayerId().isEmpty()) return Optional.empty();
        return players.existsById(a.getPlayerId()) ? Optional.of(a.getPlayerId()) : Optional.empty();
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteById(AdminAuth.hash(token));
    }

    /** Squad players an unlinked account's name may be (none for a linked one). */
    @Transactional(readOnly = true)
    public List<NameMatch.Squad> possibleFor(UserAccount a) {
        if (!a.getPlayerId().isEmpty() && players.existsById(a.getPlayerId())) return List.of();
        Set<String> claimed = claimed();
        NameMatch.Result m = NameMatch.match(a.getName(), squad());
        List<NameMatch.Squad> all = m.exact() != null ? List.of(m.exact()) : m.possible();
        return all.stream().filter(p -> !claimed.contains(p.id())).toList();
    }

    /** "Yes, I'm Ehtisham Badar": links the account to a player its name may be, and takes the squad's spelling. */
    @Transactional
    public UserAccount claim(UserAccount a, String playerId) {
        NameMatch.Squad p = possibleFor(a).stream().filter(x -> x.id().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That player can't be linked to your account. Ask the admin."));
        UserAccount fresh = accounts.findById(a.getId()).orElseThrow();
        link(fresh, p);
        return fresh;
    }

    @Transactional
    public void changePassword(UserAccount a, String current, String next) {
        UserAccount fresh = accounts.findById(a.getId()).orElseThrow();
        if (!fresh.isMustChange() && !PasswordHasher.matches(current, fresh.getPasswordHash())) {
            throw new IllegalArgumentException("Your current password isn't right.");
        }
        checkPassword(next);
        fresh.setPasswordHash(PasswordHasher.hash(next));
        fresh.setMustChange(false);
    }

    /** "Forgot password": one open request per account for the admin. Says nothing about whether the email exists. */
    @Transactional
    public void forgot(String email, String note) {
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        accounts.findByEmail(mail).ifPresent(a -> {
            if (!resets.findByUserIdAndStatus(a.getId(), "open").isEmpty()) return;
            String n = note == null ? "" : note.trim();
            resets.save(new PasswordResetRequest(UUID.randomUUID().toString(), a.getId(), n.length() > 200 ? n.substring(0, 200) : n, Instant.now().toString()));
        });
    }

    // ---------- biometrics ----------

    @Transactional(readOnly = true)
    public List<Passkey> passkeysOf(UserAccount a) {
        return passkeys.findByUserId(a.getId());
    }

    @Transactional
    public Passkey addPasskey(UserAccount a, WebAuthnService.Registration r, String label) {
        WebAuthnService.NewCredential c = webAuthn.verifyRegistration(a.getId(), r);
        if (passkeys.existsById(c.id())) throw new IllegalArgumentException("Biometric sign-in is already on for this device.");
        return passkeys.save(new Passkey(c.id(), a.getId(), c.publicKey(), c.alg(), c.signCount(), AdminAuth.clip(label), Instant.now().toString()));
    }

    @Transactional
    public boolean removePasskey(UserAccount a, String id) {
        return passkeys.findById(id).filter(p -> p.getUserId().equals(a.getId())).map(p -> { passkeys.delete(p); return true; }).orElse(false);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Signed loginWithPasskey(WebAuthnService.Assertion x, boolean remember, String label) {
        Passkey p = passkeys.findById(x == null || x.id() == null ? "" : x.id())
                .orElseThrow(() -> new IllegalArgumentException("Biometric sign-in isn't on for this device any more. Sign in with your password."));
        UserAccount a = accounts.findById(p.getUserId()).orElseThrow(() -> new IllegalArgumentException("This account no longer exists."));
        long count;
        try {
            count = webAuthn.verifyAssertion(x, p.getPublicKey(), p.getAlg(), p.getSignCount());
        } catch (IllegalArgumentException e) {
            record(a.getId(), "biometric", label, false);
            throw e;
        }
        String now = Instant.now().toString();
        p.used(count, now);
        a.loggedIn(now, "biometric");
        record(a.getId(), "biometric", label, true);
        return new Signed(a, startSession(a, remember, label));
    }

    // ---------- the admin ----------

    @Transactional(readOnly = true)
    public List<UserAccount> all() {
        return accounts.findAll().stream().sorted(Comparator.comparing(UserAccount::getCreatedAt).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<LoginEvent> eventsOf(String userId) {
        return events.findByUserIdOrderByAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<PasswordResetRequest> openRequests() {
        return resets.findByStatus("open").stream().sorted(Comparator.comparing(PasswordResetRequest::getCreatedAt)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> find(String id) {
        return accounts.findById(id);
    }

    @Transactional(readOnly = true)
    public long passkeyCount(String userId) {
        return passkeys.findByUserId(userId).size();
    }

    /**
     * Gives the account a temporary password (returned once, for the admin to pass on) that must be changed at
     * the next sign-in. Signs it out everywhere, turns biometric sign-in off and closes its reset requests.
     */
    @Transactional
    public String resetPassword(String userId) {
        UserAccount a = accounts.findById(userId).orElseThrow(() -> new IllegalArgumentException("No such account."));
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) temp.append('-');
            temp.append(TEMP_LETTERS.charAt(RANDOM.nextInt(TEMP_LETTERS.length())));
        }
        a.setPasswordHash(PasswordHasher.hash(temp.toString()));
        a.setMustChange(true);
        a.failed(0, "");
        sessions.deleteAll(sessions.findByUserId(userId));
        passkeys.deleteAll(passkeys.findByUserId(userId));
        resets.findByUserIdAndStatus(userId, "open").forEach(r -> r.setStatus("done"));
        return temp.toString();
    }

    @Transactional
    public void declineRequest(String requestId) {
        resets.findById(requestId).ifPresent(r -> r.setStatus("declined"));
    }

    /** The admin links an account to a player (or unlinks it with ''); a player has one account at most. */
    @Transactional
    public UserAccount setPlayer(String userId, String playerId) {
        UserAccount a = accounts.findById(userId).orElseThrow(() -> new IllegalArgumentException("No such account."));
        if (playerId == null || playerId.isBlank()) {
            a.setPlayerId("");
            return a;
        }
        Player p = players.findById(playerId).orElseThrow(() -> new IllegalArgumentException("No such player."));
        accounts.findByPlayerId(playerId).filter(o -> !o.getId().equals(userId))
                .ifPresent(o -> { throw new IllegalArgumentException(p.getName() + " already has an account (" + o.getEmail() + ")."); });
        link(a, new NameMatch.Squad(p.getId(), p.getName()));
        return a;
    }

    @Transactional
    public boolean delete(String userId) {
        if (!accounts.existsById(userId)) return false;
        sessions.deleteAll(sessions.findByUserId(userId));
        passkeys.deleteAll(passkeys.findByUserId(userId));
        events.deleteAll(events.findByUserIdOrderByAtDesc(userId));
        resets.findByUserIdAndStatus(userId, "open").forEach(resets::delete);
        accounts.deleteById(userId);
        return true;
    }

    // ---------- helpers ----------

    private void link(UserAccount a, NameMatch.Squad p) {
        a.setPlayerId(p.id());
        a.setName(p.name());
    }

    private List<NameMatch.Squad> squad() {
        return players.findAll().stream().map(p -> new NameMatch.Squad(p.getId(), p.getName())).toList();
    }

    private Set<String> claimed() {
        return accounts.findAll().stream().map(UserAccount::getPlayerId).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private String startSession(UserAccount a, boolean remember, String label) {
        String token = AdminAuth.randomToken();
        Instant now = Instant.now();
        sessions.save(new UserSession(AdminAuth.hash(token), a.getId(), AdminAuth.clip(label), now.toString(),
                now.plus(remember ? REMEMBER : SHORT).toString()));
        return token;
    }

    private void record(String userId, String method, String label, boolean ok) {
        events.save(new LoginEvent(UUID.randomUUID().toString(), userId, Instant.now().toString(), method, AdminAuth.clip(label), ok));
        List<LoginEvent> list = events.findByUserIdOrderByAtDesc(userId);
        if (list.size() > KEEP_EVENTS) events.deleteAll(list.subList(KEEP_EVENTS, list.size()));
    }

    private static String email(String email) {
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (mail.length() > 120 || !EMAIL.matcher(mail).matches()) throw new IllegalArgumentException("Enter a valid email address.");
        return mail;
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Use a password of at least " + MIN_PASSWORD + " characters.");
        }
        if (password.length() > 200) throw new IllegalArgumentException("That password is too long.");
    }
}

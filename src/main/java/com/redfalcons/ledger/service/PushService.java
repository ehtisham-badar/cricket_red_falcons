package com.redfalcons.ledger.service;

import com.redfalcons.ledger.model.PushMessage;
import com.redfalcons.ledger.model.PushSubscription;
import com.redfalcons.ledger.repo.PushMessageRepository;
import com.redfalcons.ledger.repo.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web push without payload encryption: each push is empty (signed with VAPID), and the service worker
 * then fetches the waiting messages for its device from {@link #pending}. Keys come from
 * LEDGER_VAPID_PUBLIC / LEDGER_VAPID_PRIVATE (base64url); without them push is switched off.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    /** Who the push services can contact about this sender (a URL, so no e-mail address is shared). */
    private static final String SUBJECT = "https://red-falcons-ledger.vercel.app";
    private static final Duration MESSAGE_LIFETIME = Duration.ofDays(3);

    public record Message(String title, String body) {
    }

    private final PushSubscriptionRepository subscriptions;
    private final PushMessageRepository messages;
    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService sender = Executors.newFixedThreadPool(4);

    public PushService(PushSubscriptionRepository subscriptions, PushMessageRepository messages,
                       @Value("${ledger.vapid-public:}") String publicKey, @Value("${ledger.vapid-private:}") String privateKey) {
        this.subscriptions = subscriptions;
        this.messages = messages;
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.isBlank() || this.publicKey.isEmpty() ? null : loadKey(privateKey.trim());
    }

    public boolean enabled() {
        return privateKey != null;
    }

    public String publicKey() {
        return enabled() ? publicKey : "";
    }

    /** Adds or updates this device; admin devices are marked so admin-only messages can reach them. */
    @Transactional
    public void subscribe(String endpoint, String playerId, boolean admin) {
        String id = idOf(endpoint);
        subscriptions.save(new PushSubscription(id, endpoint, playerId == null ? "" : playerId.trim(), admin, Instant.now().toString()));
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        String id = idOf(endpoint);
        messages.deleteBySubscriptionId(id);
        subscriptions.deleteById(id);
    }

    /**
     * Queues a message for every device (playerIds null) or for the devices of the given players, and wakes
     * them with an empty push. Returns how many devices it was queued for.
     */
    @Transactional
    public int send(String title, String body, Collection<String> playerIds) {
        return queue(subscriptions.findAll().stream()
                .filter(s -> playerIds == null || playerIds.contains(s.getPlayerId())).toList(), title, body);
    }

    /** Like {@link #send} but only to devices signed in as the admin (manager requests and the like). */
    @Transactional
    public int sendToAdmins(String title, String body) {
        return queue(subscriptions.findAll().stream().filter(PushSubscription::isAdmin).toList(), title, body);
    }

    private int queue(List<PushSubscription> targets, String title, String body) {
        String now = Instant.now().toString();
        for (PushSubscription s : targets) {
            messages.save(new PushMessage(UUID.randomUUID().toString(), s.getId(), clip(title, 80), clip(body, 300), now));
        }
        // Wake devices only once the messages are committed, or a quick device could find nothing waiting.
        if (enabled() && !targets.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    targets.forEach(s -> sender.submit(() -> wake(s)));
                }
            });
        }
        return targets.size();
    }

    /** The messages waiting for this device (newest few, at most three days old); they count as delivered. */
    @Transactional
    public List<Message> pending(String endpoint) {
        String cutoff = Instant.now().minus(MESSAGE_LIFETIME).toString();
        List<Message> out = new ArrayList<>();
        for (PushMessage m : messages.findBySubscriptionIdAndDeliveredFalseOrderByCreatedAt(idOf(endpoint))) {
            m.markDelivered();
            if (m.getCreatedAt().compareTo(cutoff) >= 0) out.add(new Message(m.getTitle(), m.getBody()));
        }
        return out.size() > 5 ? out.subList(out.size() - 5, out.size()) : out;
    }

    private void wake(PushSubscription s) {
        try {
            URI uri = URI.create(s.getEndpoint());
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "86400")
                    .header("Urgency", "high")
                    .header("Authorization", "vapid t=" + jwt(audience) + ", k=" + publicKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                // The device turned notifications off (repository calls run in their own transactions).
                messages.deleteBySubscriptionId(s.getId());
                subscriptions.deleteById(s.getId());
            } else if (status >= 400) {
                log.warn("Push to {} failed with {}", audience, status);
            }
        } catch (Exception e) {
            log.warn("Push failed: {}", e.getMessage());
        }
    }

    private String jwt(String audience) throws GeneralSecurityException {
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + SUBJECT + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(privateKey);
        sig.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + B64.encodeToString(sig.sign());
    }

    private static ECPrivateKey loadKey(String d) {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
            BigInteger s = new BigInteger(1, Base64.getUrlDecoder().decode(d));
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(s, spec));
        } catch (Exception e) {
            log.warn("Push is off: the VAPID private key could not be read ({}).", e.getMessage());
            return null;
        }
    }

    private static String idOf(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(endpoint).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}

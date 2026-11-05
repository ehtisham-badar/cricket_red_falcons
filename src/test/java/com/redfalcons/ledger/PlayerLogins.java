package com.redfalcons.ledger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Signs a test in as a squad player, the way a real player is: an account (here linked to the player by the
 * admin) and its session token, sent as X-User-Token. One account per player, made the first time it's needed.
 */
final class PlayerLogins {

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;
    private final String adminKey;
    private final Map<String, String> tokens = new HashMap<>();
    private String admin;

    PlayerLogins(int port, String adminKey) {
        this.port = port;
        this.adminKey = adminKey;
    }

    /** The session token of an account linked to {@code playerId} (unlinked if there's no such player). */
    String token(String playerId) {
        return tokens.computeIfAbsent(playerId, this::create);
    }

    private String create(String playerId) {
        try {
            String body = send("POST", "/api/account/signup", "{\"email\":\"" + playerId.toLowerCase() + "@test.example\",\"password\":\"secret123\",\"name\":\"Tester "
                    + playerId + "\"}", null);
            String id = field(body, "id"), token = field(body, "token");
            if (admin == null) admin = field(send("POST", "/api/login", "{\"name\":\"" + adminKey + "\"}", null), "token");
            send("PUT", "/api/account/users/" + id + "/player", "{\"playerId\":\"" + playerId + "\"}", admin);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String send(String method, String path, String body, String adminToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (adminToken != null) b.header("X-Admin-Token", adminToken);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String field(String json, String name) {
        return json.replaceAll("(?s).*?\"" + name + "\":\"([^\"]*)\".*", "$1");
    }
}

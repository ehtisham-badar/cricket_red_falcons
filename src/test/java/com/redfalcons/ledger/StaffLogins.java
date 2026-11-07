package com.redfalcons.ledger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Makes a test account and gives it staff rights the way the admin does (Accounts tab), then signs it in:
 * the sign-in response carries the manager or admin session token.
 */
final class StaffLogins {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    record Staff(String id, String email, String token) {
    }

    /** Signs up {@code name}, gives it {@code role} (admin token needed) and signs in; token is the staff token, or "" for no role. */
    static Staff create(int port, String adminToken, String name, String role) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "@staff.example";
        String signup = send(port, "POST", "/api/account/signup", "{\"email\":\"" + email + "\",\"password\":\"secret123\",\"name\":\"" + name + "\"}", null);
        String id = field(signup, "id");
        if (role != null && !role.isEmpty()) {
            send(port, "PUT", "/api/account/users/" + id + "/role", "{\"role\":\"" + role + "\"}", adminToken);
        }
        return new Staff(id, email, signIn(port, email, role));
    }

    /** Signs the account in again and returns the staff token its role gives. */
    static String signIn(int port, String email, String role) throws Exception {
        String body = send(port, "POST", "/api/account/login", "{\"email\":\"" + email + "\",\"password\":\"secret123\"}", null);
        return "admin".equals(role) ? field(body, "adminToken") : "manager".equals(role) ? field(body, "managerToken") : "";
    }

    static String send(int port, String method, String path, String body, String adminToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (adminToken != null) b.header("X-Admin-Token", adminToken);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static String field(String json, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }
}

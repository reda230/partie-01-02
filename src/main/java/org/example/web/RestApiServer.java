package org.example.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import org.example.db.DatabaseManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Partie 6 — API REST embarquée (com.sun.net.httpserver)
 * Pas de dépendance externe, tourne sur le port 8080.
 *
 * Endpoints :
 *   POST /api/login            body: {"username":"x","password":"y"}
 *   GET  /api/emails?user=x&token=t
 *   GET  /api/email?id=n&token=t
 *   POST /api/send             body: {"token":"t","from":"x","to":"y","subject":"s","body":"b"}
 *   DELETE /api/email?id=n&token=t
 *   POST /api/logout           body: {"token":"t"}
 */
public class RestApiServer {

    private static final int PORT = 8080;

    // token → username  (session en mémoire)
    private static final Map<String, String> sessions = new HashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/login",    RestApiServer::handleLogin);
        server.createContext("/api/logout",   RestApiServer::handleLogout);
        server.createContext("/api/register", RestApiServer::handleRegister);
        server.createContext("/api/emails",   RestApiServer::handleEmails);
        server.createContext("/api/email",    RestApiServer::handleEmail);
        server.createContext("/api/send",     RestApiServer::handleSend);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("REST API started on http://localhost:" + PORT);
    }

    // ── CORS helper ──────────────────────────────────────────────────────
    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    }

    private static void respond(HttpExchange ex, int code, String json) throws IOException {
        addCors(ex);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ── Simple JSON helpers (pas de lib externe) ─────────────────────────
    private static String jsonStr(String key, String value) {
        return "\"" + key + "\":\"" + escape(value) + "\"";
    }
    private static String jsonInt(String key, int value) {
        return "\"" + key + "\":" + value;
    }
    private static String jsonBool(String key, boolean value) {
        return "\"" + key + "\":" + value;
    }
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Parse "key":"value" pairs from a flat JSON object (simple, no nesting). */
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        // Remove outer braces
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}"))  json = json.substring(0, json.length() - 1);
        // Split on commas not inside quotes (simplified)
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replaceAll("^\"|\"$", "");
                String v = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(k, v);
            }
        }
        return map;
    }

    /** Parse query string into map. */
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    // ── Token helpers ────────────────────────────────────────────────────
    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String getUserFromToken(String token) {
        return sessions.get(token);
    }

    // ====================================================================
    //  POST /api/login
    //  body: {"username":"alice","password":"password123"}
    //  → {"success":true,"token":"...","username":"alice"}
    // ====================================================================
    private static void handleLogin(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST"))   { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }

        Map<String, String> body = parseJson(readBody(ex));
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            respond(ex, 400, "{\"error\":\"Missing username or password\"}"); return;
        }

        try {
            if (DatabaseManager.getInstance().authenticate(username, password)) {
                String token = generateToken();
                sessions.put(token, username);
                respond(ex, 200, "{" + jsonBool("success", true) + "," +
                        jsonStr("token", token) + "," +
                        jsonStr("username", username) + "}");
            } else {
                respond(ex, 401, "{\"success\":false,\"error\":\"Invalid credentials\"}");
            }
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // ====================================================================
    //  POST /api/register
    //  body: {"username":"x","password":"y"}
    //  → {"success":true} ou {"success":false,"error":"..."}
    // ====================================================================
    private static void handleRegister(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST"))   { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }

        Map<String, String> body = parseJson(readBody(ex));
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank()) { respond(ex, 400, "{\"success\":false,\"error\":\"Nom d'utilisateur requis\"}"); return; }
        if (password == null || password.length() < 4) { respond(ex, 400, "{\"success\":false,\"error\":\"Mot de passe trop court (min 4 caractères)\"}"); return; }
        if (!username.matches("[a-zA-Z0-9_.-]+")) { respond(ex, 400, "{\"success\":false,\"error\":\"Nom d'utilisateur invalide (lettres, chiffres, . _ - uniquement)\"}"); return; }

        try {
            boolean created = DatabaseManager.getInstance().createUser(username, password);
            if (created) {
                respond(ex, 200, "{\"success\":true}");
            } else {
                respond(ex, 409, "{\"success\":false,\"error\":\"Ce nom d'utilisateur existe déjà\"}");
            }
        } catch (Exception e) {
            respond(ex, 500, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // ====================================================================
    //  POST /api/logout
    //  body: {"token":"..."}
    // ====================================================================
    private static void handleLogout(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }
        Map<String, String> body = parseJson(readBody(ex));
        String token = body.get("token");
        if (token != null) sessions.remove(token);
        respond(ex, 200, "{\"success\":true}");
    }

    // ====================================================================
    //  GET /api/emails?token=...
    //  → {"emails":[{"id":1,"sender":"...","subject":"...","sent_at":"...","is_read":false}, ...]}
    // ====================================================================
    private static void handleEmails(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }

        Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
        String token = query.get("token");
        String username = getUserFromToken(token);

        if (username == null) { respond(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }

        try (ResultSet rs = DatabaseManager.getInstance().fetchEmails(username)) {
            StringBuilder sb = new StringBuilder("{\"emails\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append("{")
                        .append(jsonInt("id", rs.getInt("id"))).append(",")
                        .append(jsonStr("sender",  rs.getString("sender"))).append(",")
                        .append(jsonStr("subject", rs.getString("subject"))).append(",")
                        .append(jsonStr("sent_at", rs.getString("sent_at"))).append(",")
                        .append(jsonBool("is_read", rs.getBoolean("is_read")))
                        .append("}");
                first = false;
            }
            sb.append("]}");
            respond(ex, 200, sb.toString());
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // ====================================================================
    //  GET /api/email?id=n&token=...
    //  → {"id":1,"sender":"...","recipient":"...","subject":"...","body":"...","sent_at":"...","is_read":true}
    // ====================================================================
    private static void handleEmail(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }

        Map<String, String> query = parseQuery(ex.getRequestURI().getQuery());
        String token    = query.get("token");
        String username = getUserFromToken(token);
        if (username == null) { respond(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }

        // GET → lire un email
        if (ex.getRequestMethod().equalsIgnoreCase("GET")) {
            String idStr = query.get("id");
            if (idStr == null) { respond(ex, 400, "{\"error\":\"Missing id\"}"); return; }
            int id;
            try { id = Integer.parseInt(idStr); } catch (NumberFormatException e) { respond(ex, 400, "{\"error\":\"Invalid id\"}"); return; }

            try {
                String sql = "SELECT * FROM emails WHERE id = ? AND recipient = ? AND is_deleted = 0";
                java.sql.Connection conn = DatabaseManager.getInstance().getConnection();
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.setString(2, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { respond(ex, 404, "{\"error\":\"Email not found\"}"); return; }
                        String json = "{" +
                                jsonInt("id",        rs.getInt("id"))            + "," +
                                jsonStr("sender",    rs.getString("sender"))     + "," +
                                jsonStr("recipient", rs.getString("recipient"))  + "," +
                                jsonStr("subject",   rs.getString("subject"))    + "," +
                                jsonStr("body",      rs.getString("body"))       + "," +
                                jsonStr("sent_at",   rs.getString("sent_at"))    + "," +
                                jsonBool("is_read",  rs.getBoolean("is_read"))   +
                                "}";
                        // Marquer comme lu
                        DatabaseManager.getInstance().markRead(id);
                        respond(ex, 200, json);
                    }
                }
            } catch (Exception e) {
                respond(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
            return;
        }

        // DELETE → supprimer un email
        if (ex.getRequestMethod().equalsIgnoreCase("DELETE")) {
            String idStr = query.get("id");
            if (idStr == null) { respond(ex, 400, "{\"error\":\"Missing id\"}"); return; }
            int id;
            try { id = Integer.parseInt(idStr); } catch (NumberFormatException e) { respond(ex, 400, "{\"error\":\"Invalid id\"}"); return; }

            try {
                DatabaseManager.getInstance().deleteEmail(id);
                respond(ex, 200, "{\"success\":true}");
            } catch (Exception e) {
                respond(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
            return;
        }

        respond(ex, 405, "{\"error\":\"Method not allowed\"}");
    }

    // ====================================================================
    //  POST /api/send
    //  body: {"token":"...","from":"alice","to":"bob","subject":"...","body":"..."}
    //  → stocke directement en DB (équivalent SMTP interne)
    // ====================================================================
    private static void handleSend(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { respond(ex, 200, "{}"); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }

        Map<String, String> body = parseJson(readBody(ex));
        String token    = body.get("token");
        String username = getUserFromToken(token);
        if (username == null) { respond(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }

        String from    = body.get("from");
        String to      = body.get("to");
        String subject = body.get("subject");
        String content = body.get("body");

        if (to == null || to.isBlank()) { respond(ex, 400, "{\"error\":\"Missing recipient\"}"); return; }

        try {
            // Vérifier que le destinataire existe
            String recipient = to.contains("@") ? to.split("@")[0] : to;
            if (!DatabaseManager.getInstance().userExists(recipient)) {
                respond(ex, 404, "{\"error\":\"Recipient not found\"}"); return;
            }
            DatabaseManager.getInstance().storeEmail(
                    from + "@example.com", recipient, subject, content
            );
            respond(ex, 200, "{\"success\":true}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }
}
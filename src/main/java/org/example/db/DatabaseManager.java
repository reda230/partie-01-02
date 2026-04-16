package org.example.db;

import java.security.MessageDigest;
import java.sql.*;

/**
 * Couche d'accès MySQL — toutes les opérations passent par des procédures stockées.
 */
public class DatabaseManager {

    private static final String URL  = "jdbc:mysql://localhost:3306/mailserver_db?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = ""; // à adapter

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() throws SQLException {
        connection = DriverManager.getConnection(URL, USER, PASS);
    }

    public static synchronized DatabaseManager getInstance() throws SQLException {
        if (instance == null || instance.connection.isClosed()) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /** Expose la connexion pour les requêtes ad-hoc (ex: API REST). */
    public Connection getConnection() { return connection; }

    // ── Authentification ─────────────────────────────────────────────────

    /** Retourne true si username/password correspondent à un compte actif. */
    public boolean authenticate(String username, String password) throws SQLException {
        String hash = sha256(password);
        try (CallableStatement cs = connection.prepareCall("{CALL authenticate_user(?, ?, ?)}")) {
            cs.setString(1, username);
            cs.setString(2, hash);
            cs.registerOutParameter(3, Types.TINYINT);
            cs.execute();
            return cs.getInt(3) > 0;
        }
    }

    // ── Gestion des utilisateurs ─────────────────────────────────────────

    public boolean createUser(String username, String password) throws SQLException {
        String sql = "INSERT IGNORE INTO users(username, password) VALUES(?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, sha256(password));
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteUser(String username) throws SQLException {
        String sql = "UPDATE users SET active = 0 WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(String username, String newPassword) throws SQLException {
        try (CallableStatement cs = connection.prepareCall("{CALL update_password(?, ?)}")) {
            cs.setString(1, username);
            cs.setString(2, sha256(newPassword));
            cs.execute();
            return true;
        }
    }

    public boolean userExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ? AND active = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── Emails ───────────────────────────────────────────────────────────

    /**
     * Stocke un email (appelé par SMTP).
     * Le sujet est extrait de la première ligne "Subject: ..." du body si présent.
     */
    public void storeEmail(String sender, String recipient, String subject, String body) throws SQLException {
        try (CallableStatement cs = connection.prepareCall("{CALL store_email(?, ?, ?, ?)}")) {
            cs.setString(1, sender);
            cs.setString(2, recipient);
            cs.setString(3, subject == null ? "" : subject);
            cs.setString(4, body);
            cs.execute();
        }
    }

    /**
     * Récupère les emails non supprimés d'un utilisateur (appelé par POP3/IMAP).
     */
    public ResultSet fetchEmails(String username) throws SQLException {
        CallableStatement cs = connection.prepareCall("{CALL fetch_emails(?)}");
        cs.setString(1, username);
        return cs.executeQuery();
    }

    /** Suppression logique d'un email (is_deleted = 1). */
    public void deleteEmail(int emailId) throws SQLException {
        try (CallableStatement cs = connection.prepareCall("{CALL delete_email(?)}")) {
            cs.setInt(1, emailId);
            cs.execute();
        }
    }

    /** Marque un email comme lu. */
    public void markRead(int emailId) throws SQLException {
        try (CallableStatement cs = connection.prepareCall("{CALL mark_read(?)}")) {
            cs.setInt(1, emailId);
            cs.execute();
        }
    }

    // ── Utilitaire SHA-256 ───────────────────────────────────────────────

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}
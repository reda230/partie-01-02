package org.example;

import org.example.db.DatabaseManager;

import java.io.*;
import java.net.*;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Partie 5 — ImapServer avec stockage MySQL.
 * Changements :
 *  - loadInbox() lit depuis MySQL.
 *  - handleStore() persiste le flag \Seen en base via markRead().
 *  - handleSearch() filtre depuis les données MySQL.
 *  - Suppression logique via deleteEmail() si EXPUNGE ajouté.
 */
public class ImapServer {
    private static final int PORT = 143;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("IMAP Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ImapSession(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/** Email chargé depuis MySQL pour IMAP. */
class ImapDbMessage {
    int     id;
    boolean seen;
    String  sender, recipient, subject, body, sentAt;

    public ImapDbMessage(int id, String sender, String recipient,
                         String subject, String body, String sentAt, boolean seen) {
        this.id = id; this.sender = sender; this.recipient = recipient;
        this.subject = subject; this.body = body;
        this.sentAt = sentAt; this.seen = seen;
    }

    public String getHeaders() {
        return "From: "    + sender    + "\r\n"
                + "To: "      + recipient + "\r\n"
                + "Subject: " + subject   + "\r\n"
                + "Date: "    + sentAt    + "\r\n";
    }

    public String getBody() { return body == null ? "" : body; }

    public String getFull() {
        return getHeaders() + "\r\n" + getBody();
    }
}

class ImapSession extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private enum State { NOT_AUTHENTICATED, AUTHENTICATED, SELECTED }
    private State state = State.NOT_AUTHENTICATED;
    private String username;
    private List<ImapDbMessage> inbox = new ArrayList<>();

    public ImapSession(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("* OK IMAP server ready");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(" ", 3);
                if (parts.length < 2) continue;
                String tag     = parts[0];
                String command = parts[1].toUpperCase();
                String args    = parts.length > 2 ? parts[2] : "";

                switch (command) {
                    case "CAPABILITY":
                        out.println("* CAPABILITY IMAP4rev1");
                        out.println(tag + " OK CAPABILITY completed");
                        break;

                    case "NOOP":
                        out.println(tag + " OK NOOP completed");
                        break;

                    case "NAMESPACE":
                        out.println("* NAMESPACE ((\"\" \"/\")) NIL NIL");
                        out.println(tag + " OK NAMESPACE completed");
                        break;

                    case "EXAMINE":
                        handleSelect(tag, args);
                        break;

                    case "LOGIN":    handleLogin(tag, args);   break;
                    case "SELECT":   handleSelect(tag, args);  break;
                    case "FETCH":    handleFetch(tag, args);   break;
                    case "STORE":    handleStore(tag, args);   break;
                    case "SEARCH":   handleSearch(tag, args);  break;
                    case "EXPUNGE":  handleExpunge(tag);       break;
                    case "LOGOUT":   handleLogout(tag); return;

                    default:
                        out.println(tag + " BAD Unknown command");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── LOGIN ────────────────────────────────────────────────────────────
    private void handleLogin(String tag, String args) {
        if (state != State.NOT_AUTHENTICATED) {
            out.println(tag + " BAD Already authenticated"); return;
        }
        String[] parts = args.split(" ");
        if (parts.length < 2) { out.println(tag + " BAD LOGIN requires username and password"); return; }

        try {
            if (DatabaseManager.getInstance().authenticate(parts[0], parts[1])) {
                username = parts[0];
                state = State.AUTHENTICATED;
                out.println(tag + " OK LOGIN completed");
            } else {
                out.println(tag + " NO Authentication failed");
            }
        } catch (Exception e) {
            out.println(tag + " NO Server error: " + e.getMessage());
        }
    }

    // ── SELECT ───────────────────────────────────────────────────────────
    private void handleSelect(String tag, String folder) {
        if (state == State.NOT_AUTHENTICATED) {
            out.println(tag + " BAD SELECT not allowed in current state"); return;
        }
        if (!folder.equalsIgnoreCase("INBOX")) {
            out.println(tag + " NO [NONEXISTENT] Folder does not exist"); return;
        }
        state = State.SELECTED;
        try {
            loadInbox();
        } catch (Exception e) {
            out.println(tag + " NO DB error: " + e.getMessage()); return;
        }
        long unseen = inbox.stream().filter(m -> !m.seen).count();
        out.println("* FLAGS (\\Seen \\Deleted)");
        out.println("* " + inbox.size() + " EXISTS");
        out.println("* " + unseen + " RECENT");
        out.println(tag + " OK [READ-WRITE] SELECT completed");
    }

    // ── FETCH ────────────────────────────────────────────────────────────
    private void handleFetch(String tag, String args) {
        if (state != State.SELECTED) { out.println(tag + " BAD FETCH not allowed in current state"); return; }
        String[] parts = args.split(" ", 2);
        int msgNum;
        try {
            msgNum = Integer.parseInt(parts[0]) - 1;
            if (msgNum < 0 || msgNum >= inbox.size()) throw new NumberFormatException();
        } catch (NumberFormatException e) { out.println(tag + " BAD Invalid message number"); return; }

        ImapDbMessage msg = inbox.get(msgNum);
        String dataItem = parts.length > 1 ? parts[1].toUpperCase() : "RFC822";

        if (dataItem.contains("BODY[HEADER]") || dataItem.contains("RFC822.HEADER")) {
            String headers = msg.getHeaders();
            out.println("* " + (msgNum+1) + " FETCH (BODY[HEADER] {" + headers.length() + "})");
            out.println(headers);
        } else if (dataItem.contains("BODY[TEXT]") || dataItem.contains("RFC822.TEXT")) {
            String body = msg.getBody();
            out.println("* " + (msgNum+1) + " FETCH (BODY[TEXT] {" + body.length() + "})");
            out.println(body);
        } else if (dataItem.contains("ENVELOPE")) {
            String envelope =
                    "(\"" + msg.sentAt + "\" " + // date
                            "\"" + msg.subject + "\" " + // subject
                            "((NIL NIL \"" + msg.sender + "\" \"example.com\")) " + // from
                            "((NIL NIL \"" + msg.sender + "\" \"example.com\")) " + // sender
                            "((NIL NIL \"" + msg.sender + "\" \"example.com\")) " + // reply-to
                            "((NIL NIL \"" + msg.recipient + "\" \"example.com\")) " + // to
                            "NIL NIL NIL NIL)";

            out.println("* " + (msgNum+1) + " FETCH (ENVELOPE " + envelope + ")");
        } else {
            // RFC822 complet
            String full = msg.getFull();
            out.println("* " + (msgNum+1) + " FETCH (FLAGS (\\" + (msg.seen ? "Seen" : "") + ") RFC822 {" + full.length() + "})");
            out.println(full);
            // Marquer comme lu automatiquement
            msg.seen = true;
            try { DatabaseManager.getInstance().markRead(msg.id); } catch (Exception ignored) {}
        }
        out.println(tag + " OK FETCH completed");
    }

    // ── STORE ────────────────────────────────────────────────────────────
    private void handleStore(String tag, String args) {
        if (state != State.SELECTED) { out.println(tag + " BAD STORE not allowed in current state"); return; }
        String[] parts = args.split(" ", 3);
        int msgNum;
        try {
            msgNum = Integer.parseInt(parts[0]) - 1;
            if (msgNum < 0 || msgNum >= inbox.size()) throw new NumberFormatException();
        } catch (NumberFormatException e) { out.println(tag + " BAD Invalid message number"); return; }

        if (parts.length > 2 && parts[2].contains("\\Seen")) {
            inbox.get(msgNum).seen = true;
            // ── Partie 5 : persistance en base ──────────────────────────
            try {
                DatabaseManager.getInstance().markRead(inbox.get(msgNum).id);
            } catch (Exception e) {
                System.err.println("markRead DB error: " + e.getMessage());
            }
        }
        String flags = inbox.get(msgNum).seen ? "(\\Seen)" : "()";
        out.println("* " + (msgNum+1) + " FETCH (FLAGS " + flags + ")");
        out.println(tag + " OK STORE completed");
    }

    // ── SEARCH ───────────────────────────────────────────────────────────
    private void handleSearch(String tag, String args) {
        if (state != State.SELECTED) { out.println(tag + " BAD SEARCH not allowed in current state"); return; }
        List<Integer> results = new ArrayList<>();
        String keyword = args.replace("\"", "").toLowerCase();
        for (int i = 0; i < inbox.size(); i++) {
            ImapDbMessage m = inbox.get(i);
            if (m.getFull().toLowerCase().contains(keyword)) results.add(i + 1);
        }
        StringBuilder sb = new StringBuilder("* SEARCH");
        for (int n : results) sb.append(" ").append(n);
        out.println(sb);
        out.println(tag + " OK SEARCH completed");
    }

    // ── EXPUNGE ──────────────────────────────────────────────────────────
    private void handleExpunge(String tag) {
        if (state != State.SELECTED) { out.println(tag + " BAD EXPUNGE not allowed in current state"); return; }
        // Pour cet exemple, EXPUNGE n'a pas d'effet immédiat (suppression via DELE POP3)
        out.println(tag + " OK EXPUNGE completed");
    }

    // ── LOGOUT ───────────────────────────────────────────────────────────
    private void handleLogout(String tag) {
        out.println("* BYE IMAP server logging out");
        out.println(tag + " OK LOGOUT completed");
    }

    // ── Partie 5 : chargement depuis MySQL ──────────────────────────────
    private void loadInbox() throws Exception {
        inbox.clear();
        try (ResultSet rs = DatabaseManager.getInstance().fetchEmails(username)) {
            while (rs.next()) {
                inbox.add(new ImapDbMessage(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("sent_at"),
                        rs.getBoolean("is_read")
                ));
            }
        }
    }
}
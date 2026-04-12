package org.example;

import org.example.db.DatabaseManager;
import org.example.rmi.AuthService;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.sql.ResultSet;
import java.util.*;

/**
 * Partie 5 — Pop3Server avec stockage MySQL.
 * Changements :
 *  - loadMailbox() lit les emails depuis MySQL (fetch_emails).
 *  - handleQuit() supprime via DatabaseManager.deleteEmail().
 *  - Les messages sont représentés par DbEmail au lieu de File.
 */
public class Pop3Server {

    private static final int PORT = 110;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Pop3Session(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/** Représente un email chargé depuis MySQL. */
class DbEmail {
    int    id;
    String sender;
    String recipient;
    String subject;
    String body;
    String sentAt;
    boolean isRead;

    public DbEmail(int id, String sender, String recipient,
                   String subject, String body, String sentAt, boolean isRead) {
        this.id = id; this.sender = sender; this.recipient = recipient;
        this.subject = subject; this.body = body;
        this.sentAt = sentAt; this.isRead = isRead;
    }

    /** Reconstitue le message RFC 5322 complet. */
    public String toRfc822() {
        return "From: " + sender + "\r\n"
                + "To: "   + recipient + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Date: " + sentAt + "\r\n"
                + "\r\n"
                + body;
    }

    public String getHeaders() {
        return "From: " + sender + "\r\n"
                + "To: "   + recipient + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Date: " + sentAt + "\r\n";
    }

    public long size() { return toRfc822().length(); }
    public String uid()  { return String.valueOf(id); }
}

class Pop3Session extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private List<DbEmail>  emails        = new ArrayList<>();
    private List<Boolean>  deletionFlags = new ArrayList<>();
    private boolean authenticated = false;

    private enum Pop3State { AUTHORIZATION, TRANSACTION, UPDATE }
    private Pop3State state = Pop3State.AUTHORIZATION;

    public Pop3Session(Socket socket) { this.socket = socket; }

    private void send(String r) { out.print(r + "\r\n"); out.flush(); }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(10 * 60 * 1000);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);
            send("+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+", 2);
                String cmd = parts[0].toUpperCase();
                String arg = parts.length > 1 ? parts[1] : "";

                switch (cmd) {
                    case "CAPA": handleCapa();         break;
                    case "USER": handleUser(arg);      break;
                    case "PASS": handlePass(arg);      break;
                    case "STAT": handleStat();         break;
                    case "LIST": handleList(arg);      break;
                    case "RETR": handleRetr(arg);      break;
                    case "DELE": handleDele(arg);      break;
                    case "RSET": handleRset();         break;
                    case "NOOP": if (requireAuth()) send("+OK"); break;
                    case "UIDL": handleUidl(arg);      break;
                    case "TOP":  handleTop(arg);       break;
                    case "QUIT": handleQuit(); return;
                    default: send("-ERR Unknown command");
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            send("-ERR Timeout");
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleCapa() {
        send("+OK Capability list follows");
        send("TOP"); send("UIDL"); send(".");
    }

    private void handleUser(String arg) {
        if (state != Pop3State.AUTHORIZATION) { send("-ERR Already authenticated"); return; }
        if (arg.isEmpty()) { send("-ERR Missing username"); return; }
        try {
            if (DatabaseManager.getInstance().userExists(arg)) {
                username = arg;
                send("+OK User accepted");
            } else {
                send("-ERR User not found");
            }
        } catch (Exception e) { send("-ERR DB error"); }
    }

    private void handlePass(String arg) {
        if (username == null) { send("-ERR Send USER first"); return; }
        if (authenticated)    { send("-ERR Already authenticated"); return; }
        try {
            if (DatabaseManager.getInstance().authenticate(username, arg)) {
                authenticated = true;
                state = Pop3State.TRANSACTION;
                loadMailbox();
                send("+OK Mailbox ready (" + countActive() + " messages)");
            } else {
                send("-ERR Invalid credentials");
            }
        } catch (Exception e) { send("-ERR DB error: " + e.getMessage()); }
    }

    // ── Partie 5 : chargement depuis MySQL ──────────────────────────────
    private void loadMailbox() throws Exception {
        emails.clear(); deletionFlags.clear();
        try (ResultSet rs = DatabaseManager.getInstance().fetchEmails(username)) {
            while (rs.next()) {
                emails.add(new DbEmail(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("sent_at"),
                        rs.getBoolean("is_read")
                ));
                deletionFlags.add(false);
            }
        }
    }

    private void handleStat() {
        if (!requireAuth()) return;
        int count = 0; long size = 0;
        for (int i = 0; i < emails.size(); i++)
            if (!deletionFlags.get(i)) { count++; size += emails.get(i).size(); }
        send("+OK " + count + " " + size);
    }

    private void handleList(String arg) {
        if (!requireAuth()) return;
        if (arg.isEmpty()) {
            send("+OK " + countActive() + " messages (" + sizeActive() + " octets)");
            for (int i = 0; i < emails.size(); i++)
                if (!deletionFlags.get(i)) send((i+1) + " " + emails.get(i).size());
            send(".");
        } else {
            int idx = parseIndex(arg);
            if (idx < 0) { send("-ERR Invalid message number"); return; }
            if (deletionFlags.get(idx)) { send("-ERR Message deleted"); return; }
            send("+OK " + (idx+1) + " " + emails.get(idx).size());
        }
    }

    private void handleRetr(String arg) {
        if (!requireAuth()) return;
        int idx = parseIndex(arg);
        if (idx < 0) { send("-ERR Invalid message number"); return; }
        if (deletionFlags.get(idx)) { send("-ERR Message deleted"); return; }
        String full = emails.get(idx).toRfc822();
        send("+OK " + full.length() + " octets");
        for (String line : full.split("\r\n"))
            send(line.startsWith(".") ? "." + line : line);
        send(".");
    }

    private void handleDele(String arg) {
        if (!requireAuth()) return;
        int idx = parseIndex(arg);
        if (idx < 0) { send("-ERR Invalid message number"); return; }
        if (deletionFlags.get(idx)) { send("-ERR Already deleted"); return; }
        deletionFlags.set(idx, true);
        send("+OK Message " + (idx+1) + " marked for deletion");
    }

    private void handleRset() {
        if (!requireAuth()) return;
        Collections.fill(deletionFlags, false);
        send("+OK " + emails.size() + " messages");
    }

    private void handleUidl(String arg) {
        if (!requireAuth()) return;
        if (arg.isEmpty()) {
            send("+OK");
            for (int i = 0; i < emails.size(); i++)
                if (!deletionFlags.get(i)) send((i+1) + " " + emails.get(i).uid());
            send(".");
        } else {
            int idx = parseIndex(arg);
            if (idx < 0 || deletionFlags.get(idx)) { send("-ERR No such message"); return; }
            send("+OK " + (idx+1) + " " + emails.get(idx).uid());
        }
    }

    private void handleTop(String arg) {
        if (!requireAuth()) return;
        String[] a = arg.trim().split("\\s+");
        if (a.length < 2) { send("-ERR Usage: TOP msg lines"); return; }
        int idx, lineCount;
        try { idx = Integer.parseInt(a[0]) - 1; lineCount = Integer.parseInt(a[1]); }
        catch (NumberFormatException e) { send("-ERR Invalid arguments"); return; }
        if (idx < 0 || idx >= emails.size()) { send("-ERR No such message"); return; }
        if (deletionFlags.get(idx)) { send("-ERR Deleted"); return; }

        send("+OK");
        String[] lines = emails.get(idx).toRfc822().split("\r\n");
        boolean inBody = false; int bodyLines = 0;
        for (String line : lines) {
            if (!inBody) {
                send(line.startsWith(".") ? "." + line : line);
                if (line.isEmpty()) inBody = true;
            } else {
                if (bodyLines >= lineCount) break;
                send(line.startsWith(".") ? "." + line : line);
                bodyLines++;
            }
        }
        send(".");
    }

    // ── Partie 5 : suppression en base ──────────────────────────────────
    private void handleQuit() {
        if (authenticated) {
            for (int i = 0; i < deletionFlags.size(); i++) {
                if (deletionFlags.get(i)) {
                    try {
                        DatabaseManager.getInstance().deleteEmail(emails.get(i).id);
                        System.out.println("Deleted email id=" + emails.get(i).id);
                    } catch (Exception e) {
                        System.err.println("Failed to delete email: " + e.getMessage());
                    }
                }
            }
        }
        send("+OK POP3 server signing off");
    }

    private int parseIndex(String arg) {
        try {
            int n = Integer.parseInt(arg.trim());
            return (n < 1 || n > emails.size()) ? -1 : n - 1;
        } catch (NumberFormatException e) { return -1; }
    }

    private int  countActive() { int c=0; for (boolean f:deletionFlags) if(!f) c++; return c; }
    private long sizeActive()  { long s=0; for (int i=0;i<emails.size();i++) if(!deletionFlags.get(i)) s+=emails.get(i).size(); return s; }
    private boolean requireAuth() {
        if (!authenticated) { send("-ERR Authentication required"); return false; }
        return true;
    }
}
package org.example;

import java.io.*;
import java.net.*;
import java.util.*;

// ============================================================
//  POP3 Server — RFC 1939 compliant
//  Corrections applied:
//   1. All responses use \r\n (CRLF) — RFC 1939 §3
//   2. STAT excludes deleted messages — RFC 1939 §5
//   3. LIST excludes deleted messages — RFC 1939 §5
//   4. RETR rejected after DELE — RFC 1939 §5
//   5. Stable message ordering (sorted by name) — RFC 1939 §8
//   6. UIDL command added — RFC 1939 §7
//   7. TOP  command added — RFC 1939 §7
//   8. CAPA command added — RFC 2449
//   9. Socket read timeout (10 min) — RFC 1939 §3
//  10. Dot-stuffing in RETR output — RFC 1939 §3
// ============================================================
public class Pop3Server {

    private static final int PORT = 110;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new Pop3Session(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Pop3Session extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private File userDir;

    // Stable, sorted list of message files — loaded once at authentication
    private List<File> emails;

    // Parallel deletion-flag list (true = marked for DELE)
    private List<Boolean> deletionFlags;

    private boolean authenticated = false;

    // ── POP3 states (for clarity) ───────────────────────────────────────
    private enum Pop3State { AUTHORIZATION, TRANSACTION, UPDATE }
    private Pop3State state = Pop3State.AUTHORIZATION;

    private static final String MAIL_ROOT = "mailserver";

    public Pop3Session(Socket socket) {
        this.socket = socket;
    }

    // ------------------------------------------------------------------ //
    //  FIX 1: send() always appends \r\n — RFC 1939 §3                   //
    // ------------------------------------------------------------------ //
    private void send(String response) {
        out.print(response + "\r\n");
        out.flush();
    }

    // ------------------------------------------------------------------ //
    //  Main loop                                                          //
    // ------------------------------------------------------------------ //
    @Override
    public void run() {
        try {
            // FIX 9: 10-minute inactivity timeout
            socket.setSoTimeout(10 * 60 * 1000);

            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);

            send("+OK POP3 server ready <" + System.currentTimeMillis() + "@example.com>");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                System.out.println("C: " + line);

                String[] parts   = line.split("\\s+", 2);
                String command   = parts[0].toUpperCase();
                String argument  = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    // ── AUTHORIZATION state ──────────────────────────────
                    case "CAPA": handleCapa();              break;  // FIX 8
                    case "USER": handleUser(argument);      break;
                    case "PASS": handlePass(argument);      break;
                    // ── TRANSACTION state ─────────────────────────────────
                    case "STAT": handleStat();              break;
                    case "LIST": handleList(argument);      break;
                    case "RETR": handleRetr(argument);      break;
                    case "DELE": handleDele(argument);      break;
                    case "RSET": handleRset();              break;
                    case "NOOP": handleNoop();              break;
                    case "UIDL": handleUidl(argument);      break;  // FIX 6
                    case "TOP":  handleTop(argument);       break;  // FIX 7
                    case "QUIT": handleQuit(); return;
                    default:
                        send("-ERR Unknown command");
                        break;
                }
            }

            // Connection dropped without QUIT — do NOT apply deletions
            if (authenticated) {
                System.err.println("Connection closed without QUIT. Deletions NOT applied.");
            }

        } catch (java.net.SocketTimeoutException e) {
            send("-ERR Autologout; idle for too long");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ------------------------------------------------------------------ //
    //  CAPA — RFC 2449                                                    //
    // ------------------------------------------------------------------ //
    private void handleCapa() {
        send("+OK Capability list follows");
        send("TOP");
        send("UIDL");
        send("RESP-CODES");
        send(".");
    }

    // ------------------------------------------------------------------ //
    //  USER                                                               //
    // ------------------------------------------------------------------ //
    private void handleUser(String arg) {
        if (state != Pop3State.AUTHORIZATION) {
            send("-ERR Already authenticated");
            return;
        }
        if (arg.isEmpty()) {
            send("-ERR Missing username");
            return;
        }
        // FIX 6 (POP3 side): verify mailbox exists
        File dir = new File(MAIL_ROOT + File.separator + arg);
        if (dir.exists() && dir.isDirectory()) {
            username = arg;
            userDir  = dir;
            send("+OK User accepted");
        } else {
            send("-ERR User not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  PASS                                                               //
    // ------------------------------------------------------------------ //
    private void handlePass(String arg) {
        if (username == null) {
            send("-ERR Send USER first");
            return;
        }
        if (authenticated) {
            send("-ERR Already authenticated");
            return;
        }
        // Password check skipped in this demo (accept any password)
        authenticated = true;
        state = Pop3State.TRANSACTION;
        loadMailbox();
        send("+OK Mailbox locked and ready (" + countActive() + " messages)");
    }

    // ------------------------------------------------------------------ //
    //  FIX 5: load mailbox with stable, sorted order — RFC 1939 §8       //
    // ------------------------------------------------------------------ //
    private void loadMailbox() {
        emails       = new ArrayList<>();
        deletionFlags = new ArrayList<>();
        File[] files = userDir.listFiles(f -> f.isFile());
        if (files != null) {
            // Sort by filename (timestamp-based names = chronological order)
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                emails.add(f);
                deletionFlags.add(false);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  NOOP                                                               //
    // ------------------------------------------------------------------ //
    private void handleNoop() {
        if (!requireAuth()) return;
        send("+OK");
    }

    // ------------------------------------------------------------------ //
    //  FIX 2: STAT excludes deleted messages — RFC 1939 §5               //
    // ------------------------------------------------------------------ //
    private void handleStat() {
        if (!requireAuth()) return;
        int count = 0;
        long size  = 0;
        for (int i = 0; i < emails.size(); i++) {
            if (!deletionFlags.get(i)) {          // skip deleted
                count++;
                size += emails.get(i).length();
            }
        }
        send("+OK " + count + " " + size);
    }

    // ------------------------------------------------------------------ //
    //  FIX 3: LIST excludes deleted messages — RFC 1939 §5               //
    //  Supports both LIST (all) and LIST n (single)                       //
    // ------------------------------------------------------------------ //
    private void handleList(String arg) {
        if (!requireAuth()) return;

        if (arg.isEmpty()) {
            // Multi-line listing: only non-deleted messages
            int count = countActive();
            long total = sizeActive();
            send("+OK " + count + " messages (" + total + " octets)");
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    send((i + 1) + " " + emails.get(i).length());
                }
            }
            send(".");
        } else {
            // Single message listing
            int index = parseIndex(arg);
            if (index < 0) { send("-ERR Invalid message number"); return; }
            if (deletionFlags.get(index)) { send("-ERR Message deleted"); return; }
            send("+OK " + (index + 1) + " " + emails.get(index).length());
        }
    }

    // ------------------------------------------------------------------ //
    //  FIX 4: RETR rejected after DELE — RFC 1939 §5                     //
    //  FIX 10: dot-stuffing in output — RFC 1939 §3                      //
    // ------------------------------------------------------------------ //
    private void handleRetr(String arg) {
        if (!requireAuth()) return;
        int index = parseIndex(arg);
        if (index < 0) { send("-ERR Invalid message number"); return; }

        // FIX 4: refuse if marked deleted
        if (deletionFlags.get(index)) {
            send("-ERR Message " + (index + 1) + " is deleted");
            return;
        }

        File emailFile = emails.get(index);
        send("+OK " + emailFile.length() + " octets");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(emailFile), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // FIX 10: dot-stuffing — prefix lines starting with "." with an extra "."
                if (line.startsWith(".")) {
                    send("." + line);
                } else {
                    send(line);
                }
            }
            send(".");
        } catch (IOException e) {
            System.err.println("Error reading message: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  DELE                                                               //
    // ------------------------------------------------------------------ //
    private void handleDele(String arg) {
        if (!requireAuth()) return;
        int index = parseIndex(arg);
        if (index < 0) { send("-ERR Invalid message number"); return; }

        if (deletionFlags.get(index)) {
            send("-ERR Message " + (index + 1) + " already deleted");
            return;
        }
        deletionFlags.set(index, true);
        send("+OK Message " + (index + 1) + " marked for deletion");
    }

    // ------------------------------------------------------------------ //
    //  RSET                                                               //
    // ------------------------------------------------------------------ //
    private void handleRset() {
        if (!requireAuth()) return;
        Collections.fill(deletionFlags, false);
        send("+OK " + emails.size() + " messages (" + sizeActive() + " octets)");
    }

    // ------------------------------------------------------------------ //
    //  FIX 6: UIDL — RFC 1939 §7                                         //
    //  Uses filename as UID (stable across sessions)                      //
    // ------------------------------------------------------------------ //
    private void handleUidl(String arg) {
        if (!requireAuth()) return;

        if (arg.isEmpty()) {
            send("+OK Unique-ID listing follows");
            for (int i = 0; i < emails.size(); i++) {
                if (!deletionFlags.get(i)) {
                    // UID = filename without extension (timestamp-based = unique & stable)
                    String uid = emails.get(i).getName().replaceAll("\\.txt$", "");
                    send((i + 1) + " " + uid);
                }
            }
            send(".");
        } else {
            int index = parseIndex(arg);
            if (index < 0) { send("-ERR Invalid message number"); return; }
            if (deletionFlags.get(index)) { send("-ERR Message deleted"); return; }
            String uid = emails.get(index).getName().replaceAll("\\.txt$", "");
            send("+OK " + (index + 1) + " " + uid);
        }
    }

    // ------------------------------------------------------------------ //
    //  FIX 7: TOP n l — RFC 1939 §7                                      //
    //  Returns headers + blank line + first l lines of body               //
    // ------------------------------------------------------------------ //
    private void handleTop(String arg) {
        if (!requireAuth()) return;
        String[] topArgs = arg.trim().split("\\s+");
        if (topArgs.length < 2) {
            send("-ERR Usage: TOP msg lines");
            return;
        }
        int index, lineCount;
        try {
            index     = Integer.parseInt(topArgs[0]) - 1;
            lineCount = Integer.parseInt(topArgs[1]);
        } catch (NumberFormatException e) {
            send("-ERR Invalid arguments");
            return;
        }
        if (index < 0 || index >= emails.size()) {
            send("-ERR No such message");
            return;
        }
        if (deletionFlags.get(index)) {
            send("-ERR Message deleted");
            return;
        }
        if (lineCount < 0) {
            send("-ERR Line count must be non-negative");
            return;
        }

        send("+OK Top of message follows");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(emails.get(index)), "UTF-8"))) {
            String line;
            boolean inBody     = false;
            int bodyLinesSent  = 0;

            while ((line = reader.readLine()) != null) {
                if (!inBody) {
                    // Still in headers
                    if (line.startsWith(".")) send("." + line); else send(line);
                    if (line.isEmpty()) inBody = true; // blank line = header/body separator
                } else {
                    // In body: only send up to lineCount lines
                    if (bodyLinesSent >= lineCount) break;
                    if (line.startsWith(".")) send("." + line); else send(line);
                    bodyLinesSent++;
                }
            }
            send(".");
        } catch (IOException e) {
            System.err.println("Error reading message for TOP: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  QUIT — apply deletions only here (UPDATE state)                    //
    // ------------------------------------------------------------------ //
    private void handleQuit() {
        if (authenticated) {
            state = Pop3State.UPDATE;
            // Apply deletions in reverse order to keep indices stable
            for (int i = deletionFlags.size() - 1; i >= 0; i--) {
                if (deletionFlags.get(i)) {
                    File f = emails.get(i);
                    if (f.delete()) {
                        System.out.println("Deleted: " + f.getName());
                    } else {
                        System.err.println("Failed to delete: " + f.getName());
                    }
                }
            }
        }
        send("+OK POP3 server signing off");
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    /** Returns the 0-based index parsed from a 1-based user argument, or -1 on error. */
    private int parseIndex(String arg) {
        try {
            int n = Integer.parseInt(arg.trim());
            if (n < 1 || n > emails.size()) return -1;
            return n - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Count non-deleted messages. */
    private int countActive() {
        int c = 0;
        for (boolean flag : deletionFlags) if (!flag) c++;
        return c;
    }

    /** Total size of non-deleted messages. */
    private long sizeActive() {
        long s = 0;
        for (int i = 0; i < emails.size(); i++) {
            if (!deletionFlags.get(i)) s += emails.get(i).length();
        }
        return s;
    }

    /** Reject commands that require authentication. */
    private boolean requireAuth() {
        if (!authenticated) {
            send("-ERR Authentication required");
            return false;
        }
        return true;
    }
}
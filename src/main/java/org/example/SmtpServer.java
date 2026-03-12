package org.example;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

// ============================================================
//  SMTP Server — RFC 5321 compliant
//  Corrections applied:
//   1. Port changed to 2525 (no root required)
//   2. All responses use \r\n (CRLF) — RFC 5321 §2.3.8
//   3. Added RSET, NOOP, VRFY commands — RFC 5321 §4.5.1
//   4. Dot-unstuffing in DATA phase — RFC 5321 §4.5.2
//   5. MAIL FROM regex accepts optional parameters (SIZE…) — RFC 5321 §4.1.1.2
//   6. RCPT TO checks recipient existence instead of auto-creating — RFC 5321 §3.3
//   7. Socket read timeout (5 min) — RFC 5321 §4.5.3.2
// ============================================================
public class SmtpServer {

    private static final int PORT = 2525; // FIX 1: use non-privileged port

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new SmtpSession(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SmtpSession extends Thread {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;  // auto-flush

    private enum SmtpState {
        CONNECTED,
        HELO_RECEIVED,
        MAIL_FROM_SET,
        RCPT_TO_SET,
        DATA_RECEIVING
    }

    private SmtpState state;
    private String sender;
    private List<String> recipients;
    private StringBuilder dataBuffer;

    // Base directory where user mailboxes are stored
    private static final String MAIL_ROOT = "mailserver";

    public SmtpSession(Socket socket) {
        this.socket = socket;
        this.state = SmtpState.CONNECTED;
        this.recipients = new ArrayList<>();
        this.dataBuffer = new StringBuilder();
    }

    // ------------------------------------------------------------------ //
    //  FIX 2: send() always appends \r\n instead of relying on println()  //
    // ------------------------------------------------------------------ //
    private void send(String response) {
        out.print(response + "\r\n");
        out.flush();
    }

    @Override
    public void run() {
        try {
            // FIX 7: 5-minute read timeout (RFC 5321 §4.5.3.2)
            socket.setSoTimeout(5 * 60 * 1000);

            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);

            send("220 smtp.example.com ESMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("C: " + line);

                // ── DATA accumulation phase ──────────────────────────────
                if (state == SmtpState.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        // End of DATA
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        recipients.clear();
                        state = SmtpState.HELO_RECEIVED;
                        send("250 OK");
                    } else {
                        // FIX 4: dot-unstuffing — RFC 5321 §4.5.2
                        // A line starting with ".." is stored as "."
                        if (line.startsWith(".")) {
                            line = line.substring(1);
                        }
                        dataBuffer.append(line).append("\r\n");
                    }
                    continue;
                }

                // ── Command dispatch ─────────────────────────────────────
                String command  = extractToken(line).toUpperCase();
                String argument = extractArgument(line);

                switch (command) {
                    case "HELO":
                    case "EHLO":
                        handleHelo(command, argument);
                        break;
                    case "MAIL":
                        handleMailFrom(argument);
                        break;
                    case "RCPT":
                        handleRcptTo(argument);
                        break;
                    case "DATA":
                        handleData();
                        break;
                    // FIX 3a: RSET — RFC 5321 §4.1.1.5
                    case "RSET":
                        handleRset();
                        break;
                    // FIX 3b: NOOP — RFC 5321 §4.1.1.9
                    case "NOOP":
                        send("250 OK");
                        break;
                    // FIX 3c: VRFY — RFC 5321 §4.1.1.6 (minimal response)
                    case "VRFY":
                        handleVrfy(argument);
                        break;
                    case "QUIT":
                        handleQuit();
                        return;
                    default:
                        send("500 5.5.1 Command unrecognized: \"" + command + "\"");
                        break;
                }
            }

            if (state == SmtpState.DATA_RECEIVING) {
                System.err.println("Connection interrupted during DATA. Email discarded.");
            }

        } catch (java.net.SocketTimeoutException e) {
            send("421 4.4.2 smtp.example.com Timeout exceeded, closing connection");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ------------------------------------------------------------------ //
    //  HELO / EHLO                                                        //
    // ------------------------------------------------------------------ //
    private void handleHelo(String command, String arg) {
        // Reset transaction state (RFC 5321 §4.1.1.1)
        state = SmtpState.HELO_RECEIVED;
        sender = null;
        recipients.clear();
        dataBuffer.setLength(0);

        if (command.equals("EHLO")) {
            // Advertise extensions
            send("250-smtp.example.com Hello " + arg);
            send("250-SIZE 10240000");
            send("250 HELP");
        } else {
            send("250 smtp.example.com Hello " + arg);
        }
    }

    // ------------------------------------------------------------------ //
    //  RSET                                                               //
    // ------------------------------------------------------------------ //
    private void handleRset() {
        // Reset to post-HELO state (RFC 5321 §4.1.1.5)
        sender = null;
        recipients.clear();
        dataBuffer.setLength(0);
        if (state != SmtpState.CONNECTED) {
            state = SmtpState.HELO_RECEIVED;
        }
        send("250 OK");
    }

    // ------------------------------------------------------------------ //
    //  VRFY                                                               //
    // ------------------------------------------------------------------ //
    private void handleVrfy(String arg) {
        if (arg == null || arg.isEmpty()) {
            send("501 5.5.4 Argument required");
            return;
        }
        // Extract username from possible address
        String username = arg.replaceAll("[<>]", "").split("@")[0].trim();
        File userDir = new File(MAIL_ROOT + File.separator + username);
        if (userDir.exists() && userDir.isDirectory()) {
            send("250 " + username + "@example.com");
        } else {
            send("550 5.1.1 User not found: " + arg);
        }
    }

    // ------------------------------------------------------------------ //
    //  MAIL FROM                                                          //
    // ------------------------------------------------------------------ //
    private void handleMailFrom(String arg) {
        if (state == SmtpState.CONNECTED) {
            send("503 5.5.1 Bad sequence: send HELO first");
            return;
        }

        // FIX 5: accept optional parameters after the address — RFC 5321 §4.1.1.2
        // Pattern: FROM:<addr> [SP params]
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]*>(\\s+.*)?$")) {
            send("501 5.5.4 Syntax error in MAIL FROM parameters");
            return;
        }

        // Extract address between < >
        int lt = arg.indexOf('<');
        int gt = arg.indexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) {
            send("501 5.5.4 Syntax error in MAIL FROM parameters");
            return;
        }
        String email = arg.substring(lt + 1, gt).trim();

        // Null sender <> is allowed (bounce messages) — RFC 5321 §4.5.5
        if (!email.isEmpty() && !isValidEmail(email)) {
            send("501 5.1.7 Bad sender address syntax");
            return;
        }

        sender = email;
        state = SmtpState.MAIL_FROM_SET;
        send("250 OK");
    }

    // ------------------------------------------------------------------ //
    //  RCPT TO                                                            //
    // ------------------------------------------------------------------ //
    private void handleRcptTo(String arg) {
        if (state != SmtpState.MAIL_FROM_SET && state != SmtpState.RCPT_TO_SET) {
            send("503 5.5.1 Bad sequence: send MAIL FROM first");
            return;
        }
        if (!arg.toUpperCase().startsWith("TO:")) {
            send("501 5.5.4 Syntax error in RCPT TO parameters");
            return;
        }

        String potentialEmail = arg.substring(3).trim();
        int lt = potentialEmail.indexOf('<');
        int gt = potentialEmail.indexOf('>');
        String email;
        if (lt >= 0 && gt > lt) {
            email = potentialEmail.substring(lt + 1, gt).trim();
        } else {
            email = potentialEmail.replaceAll("[<>]", "").trim();
        }

        if (!isValidEmail(email)) {
            send("501 5.1.3 Bad recipient address syntax");
            return;
        }

        // FIX 6: verify user exists — do NOT create silently — RFC 5321 §3.3
        String username = email.split("@")[0];
        File userDir = new File(MAIL_ROOT + File.separator + username);
        if (!userDir.exists() || !userDir.isDirectory()) {
            send("550 5.1.1 No such user here: " + email);
            return;
        }

        recipients.add(email);
        state = SmtpState.RCPT_TO_SET;
        send("250 OK");
    }

    // ------------------------------------------------------------------ //
    //  DATA                                                               //
    // ------------------------------------------------------------------ //
    private void handleData() {
        if (state != SmtpState.RCPT_TO_SET || recipients.isEmpty()) {
            send("503 5.5.1 Bad sequence: need RCPT TO first");
            return;
        }
        state = SmtpState.DATA_RECEIVING;
        send("354 Start mail input; end with <CRLF>.<CRLF>");
    }

    // ------------------------------------------------------------------ //
    //  QUIT                                                               //
    // ------------------------------------------------------------------ //
    private void handleQuit() {
        send("221 2.0.0 smtp.example.com Service closing transmission channel");
    }

    // ------------------------------------------------------------------ //
    //  Store email to disk                                                //
    // ------------------------------------------------------------------ //
    private void storeEmail(String data) {
        // Unique filename: timestamp + random suffix to avoid collisions
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());

        for (String recipient : recipients) {
            String username = recipient.split("@")[0];
            File userDir   = new File(MAIL_ROOT + File.separator + username);

            // Directory must already exist (validated in handleRcptTo)
            if (!userDir.exists()) {
                System.err.println("Mailbox directory missing for: " + username);
                continue;
            }

            File emailFile = new File(userDir, timestamp + ".txt");

            // Avoid overwrite if two emails arrive in the same millisecond
            int suffix = 1;
            while (emailFile.exists()) {
                emailFile = new File(userDir, timestamp + "_" + suffix++ + ".txt");
            }

            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(emailFile), "UTF-8"))) {
                // RFC 5322 headers
                writer.print("From: " + (sender.isEmpty() ? "<>" : sender) + "\r\n");
                writer.print("To: " + String.join(", ", recipients) + "\r\n");
                writer.print("Date: " + new SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).format(new Date()) + "\r\n");
                writer.print("\r\n");
                writer.print(data);
                System.out.println("Stored email for " + recipient + " -> " + emailFile.getName());
            } catch (IOException e) {
                System.err.println("Error storing email for " + recipient + ": " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //
    private String extractToken(String line) {
        int idx = line.indexOf(' ');
        return idx > 0 ? line.substring(0, idx) : line;
    }

    private String extractArgument(String line) {
        int idx = line.indexOf(' ');
        return idx > 0 ? line.substring(idx + 1).trim() : "";
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 && !email.contains(" ");
    }
}
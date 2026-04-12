package org.example;

import org.example.db.DatabaseManager;
import org.example.rmi.AuthService;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Partie 5 — SmtpServer avec stockage MySQL.
 * Changements par rapport à la version fichiers :
 *  - storeEmail() utilise DatabaseManager.storeEmail() au lieu de File I/O.
 *  - handleRcptTo() vérifie l'existence du destinataire via DatabaseManager.userExists().
 *  - handleMailFrom() authentifie via RMI (qui lit MySQL).
 */
public class SmtpServer {

    private static final int PORT = 2525;

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
    private PrintWriter out;

    private enum SmtpState { CONNECTED, HELO_RECEIVED, MAIL_FROM_SET, RCPT_TO_SET, DATA_RECEIVING }

    private SmtpState state = SmtpState.CONNECTED;
    private String sender;
    private List<String> recipients = new ArrayList<>();
    private StringBuilder dataBuffer = new StringBuilder();

    public SmtpSession(Socket socket) { this.socket = socket; }

    private void send(String r) { out.print(r + "\r\n"); out.flush(); }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(5 * 60 * 1000);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);
            send("220 smtp.example.com ESMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("C: " + line);

                if (state == SmtpState.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        recipients.clear();
                        state = SmtpState.HELO_RECEIVED;
                        send("250 OK");
                    } else {
                        dataBuffer.append(line.startsWith(".") ? line.substring(1) : line).append("\r\n");
                    }
                    continue;
                }

                String command  = extractToken(line).toUpperCase();
                String argument = extractArgument(line);

                switch (command) {
                    case "HELO": case "EHLO": handleHelo(command, argument); break;
                    case "MAIL": handleMailFrom(argument); break;
                    case "RCPT": handleRcptTo(argument); break;
                    case "DATA": handleData(); break;
                    case "RSET": handleRset(); break;
                    case "NOOP": send("250 OK"); break;
                    case "VRFY": handleVrfy(argument); break;
                    case "QUIT": send("221 2.0.0 Bye"); return;
                    default: send("500 5.5.1 Command unrecognized");
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            send("421 4.4.2 Timeout");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleHelo(String command, String arg) {
        state = SmtpState.HELO_RECEIVED;
        sender = null; recipients.clear(); dataBuffer.setLength(0);
        if (command.equals("EHLO")) {
            send("250-smtp.example.com Hello " + arg);
            send("250-SIZE 10240000");
            send("250 HELP");
        } else {
            send("250 smtp.example.com Hello " + arg);
        }
    }

    private void handleRset() {
        sender = null; recipients.clear(); dataBuffer.setLength(0);
        if (state != SmtpState.CONNECTED) state = SmtpState.HELO_RECEIVED;
        send("250 OK");
    }

    private void handleVrfy(String arg) {
        if (arg == null || arg.isEmpty()) { send("501 5.5.4 Argument required"); return; }
        String username = arg.replaceAll("[<>]", "").split("@")[0].trim();
        try {
            if (DatabaseManager.getInstance().userExists(username))
                send("250 " + username + "@example.com");
            else
                send("550 5.1.1 User not found");
        } catch (Exception e) {
            send("451 DB error");
        }
    }

    private void handleMailFrom(String arg) {
        if (state == SmtpState.CONNECTED) { send("503 5.5.1 Send HELO first"); return; }
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]*>(\\s+.*)?$")) {
            send("501 5.5.4 Syntax error"); return;
        }
        String email = arg.substring(arg.indexOf('<') + 1, arg.indexOf('>')).trim();
        if (!email.isEmpty() && !isValidEmail(email)) { send("501 5.1.7 Bad sender"); return; }

        try {
            AuthService auth = (AuthService) Naming.lookup("rmi://localhost/AuthService");
            // SMTP vérifie seulement que l'expéditeur existe (pas son mot de passe ici)
            String username = email.split("@")[0];
            if (!DatabaseManager.getInstance().userExists(username)) {
                send("550 5.1.1 Sender not found"); return;
            }
            sender = email;
            state = SmtpState.MAIL_FROM_SET;
            send("250 OK");
        } catch (Exception e) {
            send("451 Authentication service error");
        }
    }

    private void handleRcptTo(String arg) {
        if (state != SmtpState.MAIL_FROM_SET && state != SmtpState.RCPT_TO_SET) {
            send("503 5.5.1 Send MAIL FROM first"); return;
        }
        if (!arg.toUpperCase().startsWith("TO:")) { send("501 5.5.4 Syntax error"); return; }

        String potentialEmail = arg.substring(3).trim();
        int lt = potentialEmail.indexOf('<'), gt = potentialEmail.indexOf('>');
        String email = (lt >= 0 && gt > lt)
                ? potentialEmail.substring(lt + 1, gt).trim()
                : potentialEmail.replaceAll("[<>]", "").trim();

        if (!isValidEmail(email)) { send("501 5.1.3 Bad recipient"); return; }

        // ── Partie 5 : vérification en base ─────────────────────────────
        String username = email.split("@")[0];
        try {
            if (!DatabaseManager.getInstance().userExists(username)) {
                send("550 5.1.1 No such user: " + email); return;
            }
        } catch (Exception e) {
            send("451 DB error"); return;
        }

        recipients.add(email);
        state = SmtpState.RCPT_TO_SET;
        send("250 OK");
    }

    private void handleData() {
        if (state != SmtpState.RCPT_TO_SET || recipients.isEmpty()) {
            send("503 5.5.1 Need RCPT TO first"); return;
        }
        state = SmtpState.DATA_RECEIVING;
        send("354 Start mail input; end with <CRLF>.<CRLF>");
    }

    // ── Partie 5 : stockage MySQL ────────────────────────────────────────
    private void storeEmail(String data) {
        // Extraction basique du sujet depuis les headers
        String subject = "";
        for (String line : data.split("\r\n")) {
            if (line.toLowerCase().startsWith("subject:")) {
                subject = line.substring(8).trim();
                break;
            }
            if (line.isEmpty()) break; // fin des headers
        }

        for (String recipient : recipients) {
            String username = recipient.split("@")[0];
            try {
                DatabaseManager.getInstance().storeEmail(sender, username, subject, data);
                System.out.println("Stored email in DB for " + recipient);
            } catch (Exception e) {
                System.err.println("Error storing email for " + recipient + ": " + e.getMessage());
            }
        }
    }

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
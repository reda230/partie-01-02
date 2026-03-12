package org.example;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ImapServer {
    private static final int PORT = 143; // Port IMAP standard (non privilégié si <1024)
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("IMAP Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new ImapSession(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Classe représentant un message IMAP
class ImapMessage {
    int uid;
    boolean seen;
    File file;

    public ImapMessage(int uid, File file) {
        this.uid = uid;
        this.file = file;
        this.seen = false;
    }

    public String getHeader() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder header = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break; // fin des headers
            header.append(line).append("\r\n");
        }
        reader.close();
        return header.toString();
    }

    public String getBody() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder body = new StringBuilder();
        String line;
        boolean inBody = false;
        while ((line = reader.readLine()) != null) {
            if (inBody) body.append(line).append("\r\n");
            if (line.isEmpty()) inBody = true;
        }
        reader.close();
        return body.toString();
    }

    public String getFull() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder full = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            full.append(line).append("\r\n");
        }
        reader.close();
        return full.toString();
    }
}

// Session IMAP par client
class ImapSession extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private enum State { NOT_AUTHENTICATED, AUTHENTICATED, SELECTED }

    private State state = State.NOT_AUTHENTICATED;
    private String username;
    private List<ImapMessage> inbox = new ArrayList<>();
    private AtomicInteger uidCounter = new AtomicInteger(1); // UID unique

    public ImapSession(Socket socket) {
        this.socket = socket;
    }

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
                String tag = parts[0];
                String command = parts[1].toUpperCase();
                String args = parts.length > 2 ? parts[2] : "";

                switch(command) {
                    case "LOGIN": handleLogin(tag, args); break;
                    case "SELECT": handleSelect(tag, args); break;
                    case "FETCH": handleFetch(tag, args); break;
                    case "STORE": handleStore(tag, args); break;
                    case "SEARCH": handleSearch(tag, args); break;
                    case "LOGOUT": handleLogout(tag); return;
                    default: out.println(tag + " BAD Unknown command"); break;
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    // ---------------- COMMANDES ----------------

    private void handleLogin(String tag, String args) {
        if(state != State.NOT_AUTHENTICATED) {
            out.println(tag + " BAD Already authenticated");
            return;
        }
        String[] parts = args.split(" ");
        if(parts.length < 2) {
            out.println(tag + " BAD LOGIN requires username and password");
            return;
        }
        username = parts[0];
        // On ignore le mot de passe pour la démo
        state = State.AUTHENTICATED;
        out.println(tag + " OK LOGIN completed");
    }

    private void handleSelect(String tag, String folder) {
        if(state != State.AUTHENTICATED) {
            out.println(tag + " BAD SELECT not allowed in current state");
            return;
        }
        if(!folder.equalsIgnoreCase("INBOX")) {
            out.println(tag + " NO Folder does not exist");
            return;
        }
        state = State.SELECTED;
        loadInbox(); // charge messages depuis disque
        out.println("* FLAGS (\\Seen)");
        out.println("* " + inbox.size() + " EXISTS");
        out.println("* 0 RECENT");
        out.println(tag + " OK [READ-WRITE] SELECT completed");
    }

    private void handleFetch(String tag, String args) {
        if(state != State.SELECTED) {
            out.println(tag + " BAD FETCH not allowed in current state");
            return;
        }
        String[] parts = args.split(" ", 2);
        int msgNum;
        try {
            msgNum = Integer.parseInt(parts[0]) - 1;
            if(msgNum < 0 || msgNum >= inbox.size()) throw new NumberFormatException();
        } catch(NumberFormatException e) {
            out.println(tag + " BAD Invalid message number");
            return;
        }
        ImapMessage msg = inbox.get(msgNum);
        try {
            if(parts.length > 1 && parts[1].equalsIgnoreCase("BODY[HEADER]")) {
                out.println("* " + (msgNum+1) + " FETCH (FLAGS (\\Seen) BODY[HEADER] {" + msg.getHeader().length() + "}");
                out.println(msg.getHeader() + ")");
            } else {
                out.println("* " + (msgNum+1) + " FETCH (FLAGS (\\Seen) RFC822 {" + msg.getFull().length() + "}");
                out.println(msg.getFull() + ")");
                msg.seen = true;
            }
            out.println(tag + " OK FETCH completed");
        } catch(IOException e) {
            out.println(tag + " BAD Error reading message");
        }
    }

    private void handleStore(String tag, String args) {
        if(state != State.SELECTED) {
            out.println(tag + " BAD STORE not allowed in current state");
            return;
        }
        // Format attendu : num +FLAGS (\Seen)
        String[] parts = args.split(" ", 3);
        int msgNum;
        try {
            msgNum = Integer.parseInt(parts[0]) - 1;
            if(msgNum < 0 || msgNum >= inbox.size()) throw new NumberFormatException();
        } catch(NumberFormatException e) {
            out.println(tag + " BAD Invalid message number");
            return;
        }
        String flags = parts[2];
        if(flags.contains("\\Seen")) inbox.get(msgNum).seen = true;
        out.println("* " + (msgNum+1) + " FETCH (FLAGS (\\Seen))");
        out.println(tag + " OK STORE completed");
    }

    private void handleSearch(String tag, String args) {
        if(state != State.SELECTED) {
            out.println(tag + " BAD SEARCH not allowed in current state");
            return;
        }
        List<Integer> results = new ArrayList<>();
        String keyword = args.replace("\"","").toLowerCase();
        for(int i=0;i<inbox.size();i++) {
            try {
                String full = inbox.get(i).getFull().toLowerCase();
                if(full.contains(keyword)) results.add(i+1);
            } catch(IOException e) { /* ignore */ }
        }
        out.print("* SEARCH");
        for(int n : results) out.print(" " + n);
        out.println();
        out.println(tag + " OK SEARCH completed");
    }

    private void handleLogout(String tag) {
        out.println("* BYE IMAP server logging out");
        out.println(tag + " OK LOGOUT completed");
    }

    // ---------------- HELPERS ----------------
    private void loadInbox() {
        inbox.clear();
        File dir = new File("mailserver/" + username + "/INBOX");
        if(!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles();
        if(files != null) {
            Arrays.sort(files); // pour ordre chronologique
            for(File f : files) inbox.add(new ImapMessage(uidCounter.getAndIncrement(), f));
        }
    }
}

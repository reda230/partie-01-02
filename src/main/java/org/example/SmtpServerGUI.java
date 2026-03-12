package org.example;


import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface graphique de supervision du serveur SMTP.
 * Partie 3 du TP – Interfaces de Supervision pour la Distribution des Serveurs de Messagerie
 */
public class SmtpServerGUI extends JFrame {

    // ── Couleurs du thème ──────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(18, 22, 30);
    private static final Color BG_PANEL      = new Color(26, 32, 44);
    private static final Color BG_CARD       = new Color(34, 42, 58);
    private static final Color ACCENT_SMTP   = new Color(251, 140, 0);   // orange SMTP
    private static final Color TEXT_PRIMARY  = new Color(236, 240, 245);
    private static final Color TEXT_MUTED    = new Color(120, 140, 165);
    private static final Color SUCCESS       = new Color(72, 199, 142);
    private static final Color DANGER        = new Color(240, 82, 82);
    private static final Color CLIENT_COLOR  = new Color(100, 200, 255);
    private static final Color SERVER_COLOR  = new Color(255, 200, 80);

    // ── État serveur ───────────────────────────────────────────────────────
    private ServerSocket serverSocket;
    private Thread       acceptThread;
    private volatile boolean running = false;
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private final AtomicInteger totalMessages    = new AtomicInteger(0);
    private static final int PORT = 25;

    // ── Composants UI ─────────────────────────────────────────────────────
    private JTextPane  logPane;
    private StyledDocument logDoc;
    private JLabel     statusLabel, clientCountLabel, msgCountLabel, portLabel;
    private JButton    startBtn, stopBtn, clearBtn;
    private JProgressBar activityBar;

    public SmtpServerGUI() {
        super("SMTP Server — Supervision");
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 640);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Construction de l'interface ────────────────────────────────────────
    private void initUI() {
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_SMTP));
        header.setPreferredSize(new Dimension(0, 64));

        // Titre + badge protocole
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        left.setOpaque(false);

        JLabel icon = new JLabel("✉");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        icon.setForeground(ACCENT_SMTP);
        left.add(icon);

        JLabel title = new JLabel("SMTP Server");
        title.setFont(new Font("Consolas", Font.BOLD, 20));
        title.setForeground(TEXT_PRIMARY);
        left.add(title);

        JLabel badge = new JLabel("PORT " + PORT);
        badge.setFont(new Font("Consolas", Font.BOLD, 11));
        badge.setForeground(BG_DARK);
        badge.setBackground(ACCENT_SMTP);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        left.add(badge);

        header.add(left, BorderLayout.WEST);
        header.add(buildControls(), BorderLayout.EAST);
        return header;
    }

    private JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 16));
        p.setOpaque(false);

        startBtn = createButton("▶  Démarrer", SUCCESS);
        stopBtn  = createButton("■  Arrêter",  DANGER);
        clearBtn = createButton("⌫  Effacer",  TEXT_MUTED);

        stopBtn.setEnabled(false);

        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
        clearBtn.addActionListener(e -> clearLog());

        p.add(startBtn); p.add(stopBtn); p.add(clearBtn);
        return p;
    }

    private JButton createButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? accent.darker() : new Color(50, 55, 65));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Consolas", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(130, 32));
        return btn;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(BG_DARK);
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panneau des métriques (gauche)
        center.add(buildMetrics(), BorderLayout.WEST);

        // Panneau de log (centre)
        center.add(buildLogPanel(), BorderLayout.CENTER);
        return center;
    }

    private JPanel buildMetrics() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK);
        p.setPreferredSize(new Dimension(200, 0));

        p.add(buildMetricCard("STATUT", "ARRÊTÉ", Color.RED, true));
        p.add(Box.createVerticalStrut(8));
        p.add(buildMetricCard("CLIENTS", "0", CLIENT_COLOR, false));
        p.add(Box.createVerticalStrut(8));
        p.add(buildMetricCard("MESSAGES", "0", SUCCESS, false));
        p.add(Box.createVerticalStrut(8));

        // Barre d'activité
        JPanel actCard = new JPanel(new BorderLayout(4, 4));
        actCard.setBackground(BG_CARD);
        actCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_SMTP.darker(), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel actTitle = new JLabel("ACTIVITÉ");
        actTitle.setFont(new Font("Consolas", Font.BOLD, 10));
        actTitle.setForeground(TEXT_MUTED);
        activityBar = new JProgressBar(0, 100);
        activityBar.setBackground(BG_DARK);
        activityBar.setForeground(ACCENT_SMTP);
        activityBar.setBorderPainted(false);
        actCard.add(actTitle, BorderLayout.NORTH);
        actCard.add(activityBar, BorderLayout.CENTER);
        p.add(actCard);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JLabel currentStatusRef, currentClientsRef, currentMsgsRef;

    private JPanel buildMetricCard(String title, String value, Color color, boolean isStatus) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color.darker(), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.setMaximumSize(new Dimension(200, 80));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Consolas", Font.BOLD, 10));
        titleLbl.setForeground(TEXT_MUTED);

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(new Font("Consolas", Font.BOLD, 22));
        valueLbl.setForeground(color);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLbl, BorderLayout.CENTER);

        if (isStatus) statusLabel = valueLbl;
        else if (title.equals("CLIENTS"))  clientCountLabel = valueLbl;
        else if (title.equals("MESSAGES")) msgCountLabel    = valueLbl;

        return card;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_CARD);
        p.setBorder(new LineBorder(ACCENT_SMTP.darker(), 1, true));

        // En-tête du log
        JLabel logTitle = new JLabel("  📋 Historique des commandes");
        logTitle.setFont(new Font("Consolas", Font.BOLD, 13));
        logTitle.setForeground(ACCENT_SMTP);
        logTitle.setBackground(BG_PANEL);
        logTitle.setOpaque(true);
        logTitle.setPreferredSize(new Dimension(0, 34));
        p.add(logTitle, BorderLayout.NORTH);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(12, 15, 22));
        logPane.setFont(new Font("Consolas", Font.PLAIN, 13));
        logDoc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setBackground(BG_DARK);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_SMTP.darker()));

        portLabel = new JLabel("Port : " + PORT);
        portLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        portLabel.setForeground(TEXT_MUTED);

        JLabel proto = new JLabel("Protocole : SMTP / RFC 5321");
        proto.setFont(new Font("Consolas", Font.PLAIN, 11));
        proto.setForeground(TEXT_MUTED);

        bar.add(portLabel); bar.add(new JLabel("  |  ") {{ setForeground(TEXT_MUTED); }}); bar.add(proto);
        return bar;
    }

    // ── Serveur ────────────────────────────────────────────────────────────
    private void startServer() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            updateStatus(true);
            log("SYSTÈME", "Serveur SMTP démarré sur le port " + PORT, SUCCESS);

            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        int count = connectedClients.incrementAndGet();
                        SwingUtilities.invokeLater(() -> clientCountLabel.setText(String.valueOf(connectedClients.get())));
                        log("CONNEXION", "Nouveau client : " + client.getInetAddress().getHostAddress(), CLIENT_COLOR);
                        new GUISmtpSession(client, this).start();
                    } catch (IOException e) {
                        if (running) log("ERREUR", e.getMessage(), DANGER);
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
            pulseActivity();
        } catch (IOException e) {
            log("ERREUR", "Impossible de démarrer : " + e.getMessage(), DANGER);
        }
    }

    private void stopServer() {
        if (!running) return;
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        updateStatus(false);
        log("SYSTÈME", "Serveur SMTP arrêté.", DANGER);
    }

    private void updateStatus(boolean online) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(online ? "EN LIGNE" : "ARRÊTÉ");
            statusLabel.setForeground(online ? SUCCESS : DANGER);
            startBtn.setEnabled(!online);
            stopBtn.setEnabled(online);
        });
    }

    void onMessageStored() {
        int n = totalMessages.incrementAndGet();
        SwingUtilities.invokeLater(() -> msgCountLabel.setText(String.valueOf(n)));
    }

    void onClientDisconnected() {
        connectedClients.decrementAndGet();
        SwingUtilities.invokeLater(() -> clientCountLabel.setText(String.valueOf(connectedClients.get())));
    }

    private void pulseActivity() {
        Timer t = new Timer(80, null);
        int[] val = {0};
        boolean[] up = {true};
        t.addActionListener(e -> {
            if (!running) { activityBar.setValue(0); t.stop(); return; }
            if (up[0]) { val[0] += 5; if (val[0] >= 100) up[0] = false; }
            else        { val[0] -= 5; if (val[0] <= 0)   up[0] = true;  }
            activityBar.setValue(val[0]);
        });
        t.start();
    }

    // ── Logging ────────────────────────────────────────────────────────────
    void log(String who, String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());

                Style timeStyle = logPane.addStyle("time", null);
                StyleConstants.setForeground(timeStyle, TEXT_MUTED);
                StyleConstants.setFontFamily(timeStyle, "Consolas");
                StyleConstants.setFontSize(timeStyle, 12);
                logDoc.insertString(logDoc.getLength(), "[" + time + "] ", timeStyle);

                Style whoStyle = logPane.addStyle("who", null);
                StyleConstants.setForeground(whoStyle, color);
                StyleConstants.setBold(whoStyle, true);
                StyleConstants.setFontFamily(whoStyle, "Consolas");
                StyleConstants.setFontSize(whoStyle, 12);
                logDoc.insertString(logDoc.getLength(), who + " → ", whoStyle);

                Style msgStyle = logPane.addStyle("msg", null);
                StyleConstants.setForeground(msgStyle, TEXT_PRIMARY);
                StyleConstants.setFontFamily(msgStyle, "Consolas");
                StyleConstants.setFontSize(msgStyle, 12);
                logDoc.insertString(logDoc.getLength(), message + "\n", msgStyle);

                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void clearLog() {
        try { logDoc.remove(0, logDoc.getLength()); } catch (BadLocationException ignored) {}
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(SmtpServerGUI::new);
    }
}

// ── Session SMTP instrumentée ──────────────────────────────────────────────
class GUISmtpSession extends Thread {

    private final Socket socket;
    private final SmtpServerGUI gui;
    private BufferedReader in;
    private PrintWriter out;

    private enum State { CONNECTED, HELO_RECEIVED, MAIL_FROM_SET, RCPT_TO_SET, DATA_RECEIVING }
    private State state;
    private String sender;
    private final List<String> recipients = new ArrayList<>();
    private final StringBuilder dataBuffer = new StringBuilder();
    private static final String MAIL_ROOT = "mailserver";

    private static final Color C = new Color(251, 140, 0);   // couleur SMTP

    GUISmtpSession(Socket socket, SmtpServerGUI gui) {
        this.socket = socket; this.gui = gui;
        state = State.CONNECTED;
    }

    private void send(String r) { out.print(r + "\r\n"); out.flush(); gui.log("Serveur", r, new Color(255, 200, 80)); }

    @Override
    public void run() {
        String clientAddr = socket.getInetAddress().getHostAddress();
        try {
            socket.setSoTimeout(5 * 60 * 1000);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);
            send("220 smtp.example.com ESMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                final String logged = line;
                gui.log("Client[" + clientAddr + "]", logged, new Color(100, 200, 255));

                if (state == State.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0); recipients.clear();
                        state = State.HELO_RECEIVED;
                        send("250 OK");
                        gui.onMessageStored();
                    } else {
                        if (line.startsWith(".")) line = line.substring(1);
                        dataBuffer.append(line).append("\r\n");
                    }
                    continue;
                }

                String cmd = extractToken(line).toUpperCase();
                String arg = extractArgument(line);

                switch (cmd) {
                    case "HELO": case "EHLO": handleHelo(cmd, arg); break;
                    case "MAIL": handleMailFrom(arg); break;
                    case "RCPT": handleRcptTo(arg); break;
                    case "DATA": handleData(); break;
                    case "RSET": handleRset(); break;
                    case "NOOP": send("250 OK"); break;
                    case "VRFY": handleVrfy(arg); break;
                    case "QUIT": send("221 2.0.0 Bye"); return;
                    default: send("500 5.5.1 Command unrecognized: \"" + cmd + "\"");
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            send("421 Timeout");
        } catch (IOException e) {
            gui.log("ERREUR", e.getMessage(), new Color(240, 82, 82));
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            gui.onClientDisconnected();
            gui.log("DÉCONNEXION", "Client " + clientAddr + " déconnecté", new Color(120, 140, 165));
        }
    }

    private void handleHelo(String cmd, String arg) {
        state = State.HELO_RECEIVED; sender = null; recipients.clear(); dataBuffer.setLength(0);
        if (cmd.equals("EHLO")) { send("250-smtp.example.com Hello " + arg); send("250-SIZE 10240000"); send("250 HELP"); }
        else send("250 smtp.example.com Hello " + arg);
    }
    private void handleRset() { sender = null; recipients.clear(); dataBuffer.setLength(0); if (state != State.CONNECTED) state = State.HELO_RECEIVED; send("250 OK"); }
    private void handleVrfy(String arg) {
        if (arg == null || arg.isEmpty()) { send("501 Argument required"); return; }
        String user = arg.replaceAll("[<>]","").split("@")[0].trim();
        File d = new File(MAIL_ROOT + File.separator + user);
        if (d.exists()) send("250 " + user + "@example.com"); else send("550 5.1.1 User not found");
    }
    private void handleMailFrom(String arg) {
        if (state == State.CONNECTED) { send("503 5.5.1 Send HELO first"); return; }
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]*>(\\s+.*)?$")) { send("501 Syntax error"); return; }
        int lt=arg.indexOf('<'),gt=arg.indexOf('>');
        sender = arg.substring(lt+1,gt).trim();
        state = State.MAIL_FROM_SET; send("250 OK");
    }
    private void handleRcptTo(String arg) {
        if (state != State.MAIL_FROM_SET && state != State.RCPT_TO_SET) { send("503 5.5.1 Send MAIL FROM first"); return; }
        String pot = arg.substring(3).trim();
        int lt=pot.indexOf('<'),gt=pot.indexOf('>');
        String email = (lt>=0&&gt>lt) ? pot.substring(lt+1,gt).trim() : pot.replaceAll("[<>]","").trim();
        String user = email.split("@")[0];
        File d = new File(MAIL_ROOT+File.separator+user);
        if (!d.exists()||!d.isDirectory()) { send("550 5.1.1 No such user: "+email); return; }
        recipients.add(email); state = State.RCPT_TO_SET; send("250 OK");
    }
    private void handleData() {
        if (state != State.RCPT_TO_SET || recipients.isEmpty()) { send("503 Need RCPT TO first"); return; }
        state = State.DATA_RECEIVING; send("354 Start mail input; end with <CRLF>.<CRLF>");
    }
    private void storeEmail(String data) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        for (String rec : recipients) {
            String user = rec.split("@")[0];
            File dir = new File(MAIL_ROOT+File.separator+user);
            if (!dir.exists()) continue;
            File f = new File(dir, ts+".txt"); int s=1;
            while (f.exists()) f = new File(dir, ts+"_"+s+++".txt");
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new java.io.FileOutputStream(f),"UTF-8"))) {
                w.print("From: "+(sender.isEmpty()?"<>":sender)+"\r\n");
                w.print("To: "+String.join(", ",recipients)+"\r\n");
                w.print("Date: "+new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.ENGLISH).format(new Date())+"\r\n\r\n");
                w.print(data);
                gui.log("STOCKAGE", "Email enregistré pour "+rec+" → "+f.getName(), new Color(72,199,142));
            } catch (IOException e) { gui.log("ERREUR","Stockage échoué : "+e.getMessage(), new Color(240,82,82)); }
        }
    }
    private String extractToken(String l) { int i=l.indexOf(' '); return i>0?l.substring(0,i):l; }
    private String extractArgument(String l) { int i=l.indexOf(' '); return i>0?l.substring(i+1).trim():""; }
}
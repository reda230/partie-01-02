package org.example;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Lanceur unifié – ouvre les trois interfaces de supervision
 * dans un JFrame avec onglets (tabs).
 *
 * Usage :  javac *.java && java org.example.gui.MailSupervisorLauncher
 */
public class MailSupervisorLauncher extends JFrame {

    private static final Color COLOR_BG     = new Color(16, 20, 30);
    private static final Color COLOR_PANEL  = new Color(22, 28, 42);
    private static final Color COLOR_BORDER = new Color(42, 52, 72);
    private static final Color COLOR_TEXT   = new Color(220, 230, 245);
    private static final Color COLOR_DIM    = new Color(90, 105, 130);
    private static final Color TAB_SMTP     = new Color(56, 189, 248);
    private static final Color TAB_POP3     = new Color(52, 211, 153);
    private static final Color TAB_IMAP     = new Color(167, 139, 250);

    public MailSupervisorLauncher() {
        super("Supervision – Système de Messagerie Distribuée  |  TP 2025/2026");
        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // ── Bandeau supérieur ──
        add(buildBanner(), BorderLayout.NORTH);

        // ── Onglets ──
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(COLOR_BG);
        tabs.setForeground(COLOR_TEXT);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("TabbedPane.selected",    COLOR_PANEL);
        UIManager.put("TabbedPane.background",  COLOR_BG);
        UIManager.put("TabbedPane.foreground",  COLOR_TEXT);
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

        tabs.addTab("✉  SMTP",  buildSmtpPanel());
        tabs.addTab("📥 POP3",  buildPop3Panel());
        tabs.addTab("🗂  IMAP",  buildImapPanel());

        // Couleur des onglets
        tabs.setForegroundAt(0, TAB_SMTP);
        tabs.setForegroundAt(1, TAB_POP3);
        tabs.setForegroundAt(2, TAB_IMAP);

        add(tabs, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ─── Bandeau titre ────────────────────────────────────────────────────────
    private JPanel buildBanner() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));
        p.setPreferredSize(new Dimension(0, 60));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 12));
        left.setOpaque(false);

        JLabel logo = new JLabel("⚙");
        logo.setFont(new Font("SansSerif", Font.PLAIN, 28));
        logo.setForeground(new Color(251, 191, 36));

        JLabel title = new JLabel("Mail Server Supervisor");
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setForeground(COLOR_TEXT);

        JLabel sub = new JLabel("   Supervision SMTP · POP3 · IMAP — TP Systèmes Distribués 2025/2026");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(COLOR_DIM);

        left.add(logo);
        JPanel t = new JPanel(); t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(title); t.add(sub);
        left.add(t);
        p.add(left, BorderLayout.WEST);

        // Bouton "Tout démarrer" (commodité)
        JButton allStart = new JButton("▶  Démarrer tous");
        allStart.setFont(new Font("SansSerif", Font.BOLD, 12));
        allStart.setForeground(COLOR_BG);
        allStart.setBackground(new Color(251, 191, 36));
        allStart.setFocusPainted(false); allStart.setBorderPainted(false);
        allStart.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        allStart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        allStart.setToolTipText("Lance les trois serveurs avec leurs ports par défaut");

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        right.setOpaque(false);
        right.add(allStart);
        p.add(right, BorderLayout.EAST);

        return p;
    }

    // ─── Onglet SMTP ─────────────────────────────────────────────────────────
    private JPanel buildSmtpPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_BG);

        JPanel info = infoBar(
                "Port par défaut : 25",
                "RFC 5321",
                TAB_SMTP,
                "Le serveur SMTP reçoit les emails entrants et les stocke dans les boîtes des destinataires."
        );
        wrapper.add(info, BorderLayout.NORTH);

        // ── Intégration de SmtpServerGUI sans créer un nouveau JFrame ──
        SmtpEmbedded smtp = new SmtpEmbedded();
        wrapper.add(smtp, BorderLayout.CENTER);
        return wrapper;
    }

    // ─── Onglet POP3 ─────────────────────────────────────────────────────────
    private JPanel buildPop3Panel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_BG);

        JPanel info = infoBar(
                "Port par défaut : 110",
                "RFC 1939",
                TAB_POP3,
                "Le serveur POP3 permet aux clients de télécharger et supprimer leurs messages."
        );
        wrapper.add(info, BorderLayout.NORTH);

        Pop3Embedded pop3 = new Pop3Embedded();
        wrapper.add(pop3, BorderLayout.CENTER);
        return wrapper;
    }

    // ─── Onglet IMAP ─────────────────────────────────────────────────────────
    private JPanel buildImapPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_BG);

        JPanel info = infoBar(
                "Port par défaut : 143",
                "RFC 9051",
                TAB_IMAP,
                "Le serveur IMAP offre un accès synchronisé aux boîtes, avec gestion des états et des dossiers."
        );
        wrapper.add(info, BorderLayout.NORTH);

        ImapEmbedded imap = new ImapEmbedded();
        wrapper.add(imap, BorderLayout.CENTER);
        return wrapper;
    }

    // ─── Barre d'info ─────────────────────────────────────────────────────────
    private JPanel infoBar(String port, String rfc, Color accent, String desc) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 6));
        bar.setBackground(new Color(20, 24, 36));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));

        bar.add(badge(port, accent));
        bar.add(badge(rfc,  accent));

        JLabel d = new JLabel(desc);
        d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        d.setForeground(COLOR_DIM);
        bar.add(d);
        return bar;
    }

    private JLabel badge(String text, Color color) {
        JLabel l = new JLabel(" " + text + " ");
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(color);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        return l;
    }

    // ─── Pied de page ────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        bar.setBackground(COLOR_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER));
        JLabel l = new JLabel("Chaque serveur fonctionne indépendamment dans son propre thread — compatible déploiement distribué multi-machine");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(COLOR_DIM);
        bar.add(l);
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(MailSupervisorLauncher::new);
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  Versions "embedded" des interfaces (JPanel au lieu de JFrame)
//  – reprennent toute la logique des classes GUI principales
//  – permettent l'intégration dans le launcher à onglets
// ══════════════════════════════════════════════════════════════════════════════

// ─── SMTP embarqué ───────────────────────────────────────────────────────────
class SmtpEmbedded extends JPanel {

    private static final Color BG   = new Color(18, 22, 34);
    private static final Color PNL  = new Color(26, 32, 48);
    private static final Color BDR  = new Color(45, 55, 80);
    private static final Color ACC  = new Color(56, 189, 248);
    private static final Color GRN  = new Color(52, 211, 153);
    private static final Color RED  = new Color(248, 113, 113);
    private static final Color YLW  = new Color(251, 191, 36);
    private static final Color TXT  = new Color(226, 232, 240);
    private static final Color DIM  = new Color(100, 116, 139);
    private static final Color CLT  = new Color(167, 243, 208);
    private static final Color SRV  = new Color(147, 197, 253);
    private static final Font  MONO = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font  BOLD = new Font("SansSerif",  Font.BOLD,  13);
    private static final String MAIL_ROOT = "mailserver";

    private JTextPane logPane; private StyledDocument doc;
    private JLabel statusLbl, connLbl, emailLbl;
    private JButton startBtn, stopBtn;
    private JTextField portField;
    private Style sTS, sCLT, sSRV, sINFO, sERR;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final java.util.concurrent.atomic.AtomicInteger clients = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger emails  = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newCachedThreadPool();

    SmtpEmbedded() { setBackground(BG); setLayout(new BorderLayout()); build(); }

    private void build() {
        // ── top bar ──
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        top.setBackground(PNL);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BDR));
        top.add(lbl("Port :")); portField = field("25", 6); top.add(portField);
        startBtn = btn("▶ Démarrer", GRN); stopBtn = btn("■ Arrêter", RED);
        JButton clr = btn("⌫ Effacer", DIM);
        stopBtn.setEnabled(false);
        startBtn.addActionListener(e -> start());
        stopBtn.addActionListener(e  -> stop());
        clr.addActionListener(e      -> clearLog());
        top.add(startBtn); top.add(stopBtn); top.add(clr);

        // stats
        statusLbl = stat("● Arrêté", RED); connLbl = stat("0", ACC); emailLbl = stat("0", GRN);
        top.add(Box.createHorizontalStrut(20));
        top.add(statCard(statusLbl, "État")); top.add(statCard(connLbl, "Clients")); top.add(statCard(emailLbl, "Emails"));
        add(top, BorderLayout.NORTH);

        // ── log pane ──
        logPane = new JTextPane(); logPane.setEditable(false);
        logPane.setBackground(new Color(12, 16, 26)); logPane.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        logPane.setFont(MONO); doc = logPane.getStyledDocument();
        JScrollPane sc = new JScrollPane(logPane);
        sc.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BDR),
                " Journal SMTP ", TitledBorder.LEFT, TitledBorder.TOP, BOLD, ACC));
        sc.getViewport().setBackground(new Color(12, 16, 26));
        JPanel center = new JPanel(new BorderLayout()); center.setBackground(BG);
        center.setBorder(BorderFactory.createEmptyBorder(8,8,8,8)); center.add(sc);
        add(center, BorderLayout.CENTER);

        // styles
        sTS   = mkStyle(DIM, 11); sCLT = mkStyle(CLT, 12);
        sSRV  = mkStyle(SRV, 12); sINFO = mkStyle(YLW, 12); sERR = mkStyle(RED, 12);
    }

    private Style mkStyle(Color c, int sz) {
        Style s = logPane.addStyle(c.toString() + sz, null);
        StyleConstants.setForeground(s, c);
        StyleConstants.setFontFamily(s, "Monospaced");
        StyleConstants.setFontSize(s, sz);
        return s;
    }

    private void appendLog(String pfx, String msg, Style s) {
        SwingUtilities.invokeLater(() -> {
            try {
                String ts = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
                doc.insertString(doc.getLength(), "[" + ts + "] ", sTS);
                doc.insertString(doc.getLength(), pfx + " " + msg + "\n", s);
                logPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    void logClient(int id, String m) { appendLog("Client" + id + " →", m, sCLT); }
    void logServer(int id, String m) { appendLog("Serveur →", m, sSRV); }
    void logInfo(String m)           { appendLog("[INFO]",    m, sINFO); }
    void logError(String m)          { appendLog("[ERREUR]",  m, sERR); }
    void clearLog() { try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {} }

    void onEmailStored(int id, String r) {
        emails.incrementAndGet();
        SwingUtilities.invokeLater(() -> emailLbl.setText(String.valueOf(emails.get())));
        logInfo("Email stocké pour " + r + " (Client" + id + ")");
    }
    void onDisconnect(int id) {
        clients.decrementAndGet();
        SwingUtilities.invokeLater(() -> connLbl.setText(String.valueOf(clients.get())));
        logInfo("Client" + id + " déconnecté.");
    }

    private void start() {
        int port; try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException e) { JOptionPane.showMessageDialog(null, "Port invalide."); return; }
        try {
            serverSocket = new ServerSocket(port); running = true;
            startBtn.setEnabled(false); stopBtn.setEnabled(true); portField.setEnabled(false);
            statusLbl.setText("● Actif"); statusLbl.setForeground(GRN);
            logInfo("Serveur SMTP démarré sur le port " + port);
            Thread t = new Thread(() -> {
                while (running) {
                    try {
                        Socket c = serverSocket.accept();
                        int id = clients.incrementAndGet();
                        SwingUtilities.invokeLater(() -> connLbl.setText(String.valueOf(clients.get())));
                        logInfo("Connexion " + c.getInetAddress() + " (Client" + id + ")");
                        pool.submit(new SmtpSessionEmbed(c, id, this));
                    } catch (IOException e) { if (running) logError(e.getMessage()); }
                }
            }, "smtp-accept"); t.setDaemon(true); t.start();
        } catch (IOException e) { logError("Démarrage impossible: " + e.getMessage()); }
    }

    private void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        startBtn.setEnabled(true); stopBtn.setEnabled(false); portField.setEnabled(true);
        statusLbl.setText("● Arrêté"); statusLbl.setForeground(RED);
        logInfo("Serveur SMTP arrêté.");
    }

    // helpers
    private JLabel lbl(String t) { JLabel l = new JLabel(t); l.setForeground(DIM); l.setFont(new Font("SansSerif",Font.PLAIN,12)); return l; }
    private JTextField field(String v, int c) {
        JTextField f = new JTextField(v, c); f.setBackground(BG); f.setForeground(TXT);
        f.setCaretColor(TXT); f.setBorder(BorderFactory.createLineBorder(BDR)); f.setFont(MONO); return f;
    }
    private JButton btn(String t, Color c) {
        JButton b = new JButton(t); b.setFont(BOLD); b.setForeground(BG); b.setBackground(c);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setBorder(BorderFactory.createEmptyBorder(5,12,5,12)); return b;
    }
    private JLabel stat(String t, Color c) { JLabel l = new JLabel(t, SwingConstants.CENTER); l.setFont(BOLD); l.setForeground(c); return l; }
    private JPanel statCard(JLabel l, String cap) {
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(PNL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BDR), BorderFactory.createEmptyBorder(4,10,4,10)));
        JLabel c = new JLabel(cap, SwingConstants.CENTER); c.setFont(new Font("SansSerif",Font.PLAIN,10)); c.setForeground(DIM);
        p.add(l, BorderLayout.CENTER); p.add(c, BorderLayout.SOUTH); return p;
    }
}

// ─── SMTP session pour SmtpEmbedded ──────────────────────────────────────────
class SmtpSessionEmbed implements Runnable {
    private final Socket socket; private final int id; private final SmtpEmbedded gui;
    private BufferedReader in; private PrintWriter out;
    private enum S { CON, HELO, MAIL, RCPT, DATA } private S state = S.CON;
    private String sender; private final java.util.List<String> rcpts = new java.util.ArrayList<>();
    private final StringBuilder buf = new StringBuilder();
    private static final String ROOT = "mailserver";

    SmtpSessionEmbed(Socket s, int id, SmtpEmbedded gui) { this.socket = s; this.id = id; this.gui = gui; }

    @Override public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), false);
            send("220 smtp.example.com ESMTP Ready");
            String line;
            while ((line = in.readLine()) != null) {
                gui.logClient(id, line);
                if (state == S.DATA) {
                    if (line.equals(".")) { store(buf.toString()); buf.setLength(0); rcpts.clear(); state = S.HELO; send("250 OK"); }
                    else { buf.append(line.startsWith(".") ? line.substring(1) : line).append("\r\n"); }
                    continue;
                }
                String cmd = (line.indexOf(' ') > 0 ? line.substring(0, line.indexOf(' ')) : line).toUpperCase();
                String arg = line.indexOf(' ') > 0 ? line.substring(line.indexOf(' ') + 1).trim() : "";
                switch (cmd) {
                    case "HELO": case "EHLO": state = S.HELO; sender = null; rcpts.clear(); send("250 Hello"); break;
                    case "MAIL": sender = arg.replaceAll("(?i)FROM:\\s*<([^>]*)>.*", "$1"); state = S.MAIL; send("250 OK"); break;
                    case "RCPT": {
                        String e = arg.replaceAll("(?i)TO:\\s*<([^>]*)>.*", "$1");
                        String u = e.split("@")[0];
                        if (!new java.io.File(ROOT + java.io.File.separator + u).exists()) { send("550 No such user"); break; }
                        rcpts.add(e); state = S.RCPT; send("250 OK"); break;
                    }
                    case "DATA": if (state == S.RCPT) { state = S.DATA; send("354 Start input"); } else send("503 Bad sequence"); break;
                    case "RSET": sender = null; rcpts.clear(); buf.setLength(0); if (state != S.CON) state = S.HELO; send("250 OK"); break;
                    case "NOOP": send("250 OK"); break;
                    case "QUIT": send("221 Bye"); return;
                    default:     send("500 Unknown command");
                }
            }
        } catch (IOException e) { /* ignore */ }
        finally { gui.onDisconnect(id); try { socket.close(); } catch (IOException ignored) {} }
    }

    private void send(String m) { out.print(m + "\r\n"); out.flush(); gui.logServer(id, m); }

    private void store(String data) {
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new java.util.Date());
        for (String r : rcpts) {
            String u = r.split("@")[0];
            java.io.File dir = new java.io.File(ROOT + java.io.File.separator + u);
            if (!dir.exists()) continue;
            java.io.File f = new java.io.File(dir, ts + ".txt"); int s = 1;
            while (f.exists()) f = new java.io.File(dir, ts + "_" + s++ + ".txt");
            try (PrintWriter w = new PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8"))) {
                w.print("From: " + sender + "\r\nTo: " + r + "\r\n\r\n" + data);
                gui.onEmailStored(id, r);
            } catch (IOException e) { gui.logError("Stockage échoué: " + e.getMessage()); }
        }
    }
}

// ─── POP3 embarqué ────────────────────────────────────────────────────────────
class Pop3Embedded extends JPanel {
    private static final Color BG=new Color(18,24,20),PNL=new Color(24,32,27),BDR=new Color(40,60,45);
    private static final Color ACC=new Color(52,211,153),GRN=new Color(74,222,128),RED=new Color(248,113,113);
    private static final Color YLW=new Color(251,191,36),TXT=new Color(220,240,230),DIM=new Color(90,120,100);
    private static final Color CLT=new Color(167,243,208),SRV=new Color(110,231,183);
    private static final Font MONO=new Font("Monospaced",Font.PLAIN,12),BOLD=new Font("SansSerif",Font.BOLD,13);

    private JTextPane logPane; private StyledDocument doc;
    private JLabel statusLbl,connLbl,cmdLbl;
    private JButton startBtn,stopBtn;
    private JTextField portField;
    private Style sTS,sCLT,sSRV,sINFO,sERR;
    private ServerSocket serverSocket; private volatile boolean running=false;
    private final java.util.concurrent.atomic.AtomicInteger clients=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger cmds=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newCachedThreadPool();

    Pop3Embedded(){setBackground(BG);setLayout(new BorderLayout());build();}

    private void build(){
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));top.setBackground(PNL);
        top.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BDR));
        top.add(lbl("Port :"));portField=field("110",6);top.add(portField);
        startBtn=btn("▶ Démarrer",GRN);stopBtn=btn("■ Arrêter",RED);JButton clr=btn("⌫ Effacer",DIM);
        stopBtn.setEnabled(false);
        startBtn.addActionListener(e->start());stopBtn.addActionListener(e->stop());clr.addActionListener(e->clearLog());
        top.add(startBtn);top.add(stopBtn);top.add(clr);
        statusLbl=stat("● Arrêté",RED);connLbl=stat("0",ACC);cmdLbl=stat("0",GRN);
        top.add(Box.createHorizontalStrut(20));
        top.add(statCard(statusLbl,"État"));top.add(statCard(connLbl,"Clients"));top.add(statCard(cmdLbl,"Commandes"));
        add(top,BorderLayout.NORTH);
        logPane=new JTextPane();logPane.setEditable(false);logPane.setBackground(new Color(10,18,13));
        logPane.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));logPane.setFont(MONO);doc=logPane.getStyledDocument();
        JScrollPane sc=new JScrollPane(logPane);
        sc.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BDR)," Journal POP3 ",TitledBorder.LEFT,TitledBorder.TOP,BOLD,ACC));
        sc.getViewport().setBackground(new Color(10,18,13));
        JPanel c=new JPanel(new BorderLayout());c.setBackground(BG);c.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));c.add(sc);
        add(c,BorderLayout.CENTER);
        sTS=mkS(DIM,11);sCLT=mkS(CLT,12);sSRV=mkS(SRV,12);sINFO=mkS(YLW,12);sERR=mkS(RED,12);
    }

    private Style mkS(Color c,int sz){Style s=logPane.addStyle("p3"+c+sz,null);StyleConstants.setForeground(s,c);StyleConstants.setFontFamily(s,"Monospaced");StyleConstants.setFontSize(s,sz);return s;}
    private void appendLog(String p,String m,Style s){SwingUtilities.invokeLater(()->{try{String ts=new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());doc.insertString(doc.getLength(),"["+ts+"] ",sTS);doc.insertString(doc.getLength(),p+" "+m+"\n",s);logPane.setCaretPosition(doc.getLength());}catch(BadLocationException ignored){}});}
    void logClient(int i,String m){appendLog("Client"+i+" →",m,sCLT);}
    void logServer(int i,String m){appendLog("Serveur →",m,sSRV);}
    void logInfo(String m){appendLog("[INFO]",m,sINFO);}
    void logError(String m){appendLog("[ERREUR]",m,sERR);}
    void clearLog(){try{doc.remove(0,doc.getLength());}catch(BadLocationException ignored){}}
    void onCmd(){int c=cmds.incrementAndGet();SwingUtilities.invokeLater(()->cmdLbl.setText(String.valueOf(c)));}
    void onDisconnect(int id){clients.decrementAndGet();SwingUtilities.invokeLater(()->connLbl.setText(String.valueOf(clients.get())));logInfo("Client"+id+" déconnecté.");}

    private void start(){
        int port;try{port=Integer.parseInt(portField.getText().trim());}catch(NumberFormatException e){JOptionPane.showMessageDialog(null,"Port invalide.");return;}
        try{serverSocket=new ServerSocket(port);running=true;startBtn.setEnabled(false);stopBtn.setEnabled(true);portField.setEnabled(false);
            statusLbl.setText("● Actif");statusLbl.setForeground(GRN);logInfo("Serveur POP3 démarré sur le port "+port);
            Thread t=new Thread(()->{while(running){try{Socket c=serverSocket.accept();int id=clients.incrementAndGet();SwingUtilities.invokeLater(()->connLbl.setText(String.valueOf(clients.get())));logInfo("Connexion "+c.getInetAddress()+" (Client"+id+")");pool.submit(new Pop3SessionEmbed(c,id,this));}catch(IOException e){if(running)logError(e.getMessage());}}});t.setDaemon(true);t.start();
        }catch(IOException e){logError("Démarrage impossible: "+e.getMessage());}
    }
    private void stop(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(IOException ignored){}pool.shutdownNow();startBtn.setEnabled(true);stopBtn.setEnabled(false);portField.setEnabled(true);statusLbl.setText("● Arrêté");statusLbl.setForeground(RED);logInfo("Serveur POP3 arrêté.");}

    private JLabel lbl(String t){JLabel l=new JLabel(t);l.setForeground(DIM);l.setFont(new Font("SansSerif",Font.PLAIN,12));return l;}
    private JTextField field(String v,int c){JTextField f=new JTextField(v,c);f.setBackground(BG);f.setForeground(TXT);f.setCaretColor(TXT);f.setBorder(BorderFactory.createLineBorder(BDR));f.setFont(MONO);return f;}
    private JButton btn(String t,Color c){JButton b=new JButton(t);b.setFont(BOLD);b.setForeground(BG);b.setBackground(c);b.setFocusPainted(false);b.setBorderPainted(false);b.setBorder(BorderFactory.createEmptyBorder(5,12,5,12));return b;}
    private JLabel stat(String t,Color c){JLabel l=new JLabel(t,SwingConstants.CENTER);l.setFont(BOLD);l.setForeground(c);return l;}
    private JPanel statCard(JLabel l,String cap){JPanel p=new JPanel(new BorderLayout());p.setBackground(PNL);p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BDR),BorderFactory.createEmptyBorder(4,10,4,10)));JLabel c=new JLabel(cap,SwingConstants.CENTER);c.setFont(new Font("SansSerif",Font.PLAIN,10));c.setForeground(DIM);p.add(l,BorderLayout.CENTER);p.add(c,BorderLayout.SOUTH);return p;}
}

class Pop3SessionEmbed implements Runnable {
    private final Socket socket;private final int id;private final Pop3Embedded gui;
    private BufferedReader in;private PrintWriter out;
    private enum S{AUTH,TRANS,UPDATE}private S state=S.AUTH;private String username;
    private java.util.List<java.io.File> msgs=new java.util.ArrayList<>();
    private java.util.Set<Integer> del=new java.util.HashSet<>();
    private static final String ROOT="mailserver";
    Pop3SessionEmbed(Socket s,int id,Pop3Embedded gui){this.socket=s;this.id=id;this.gui=gui;}
    @Override public void run(){
        try{in=new BufferedReader(new InputStreamReader(socket.getInputStream()));out=new PrintWriter(socket.getOutputStream(),true);send("+OK POP3 ready");
            String line;while((line=in.readLine())!=null){gui.logClient(id,line);gui.onCmd();String[]p=line.split(" ",2);String cmd=p[0].toUpperCase();String arg=p.length>1?p[1].trim():"";
                switch(cmd){case"USER":username=arg;send("+OK");break;case"PASS":{java.io.File d=new java.io.File(ROOT+java.io.File.separator+username);if(!d.exists()){send("-ERR Auth failed");break;}load(d);state=S.TRANS;send("+OK "+msgs.size()+" messages");break;}
                    case"STAT":{long total=msgs.stream().mapToLong(java.io.File::length).sum();send("+OK "+msgs.size()+" "+total);break;}
                    case"LIST":{send("+OK");for(int i=0;i<msgs.size();i++)if(!del.contains(i))send((i+1)+" "+msgs.get(i).length());send(".");break;}
                    case"RETR":{try{int n=Integer.parseInt(arg)-1;if(n<0||n>=msgs.size()){send("-ERR No msg");break;}send("+OK");try(BufferedReader r=new BufferedReader(new java.io.FileReader(msgs.get(n)))){String l;while((l=r.readLine())!=null)out.println(l.startsWith(".")?"."+l:l);}send(".");}catch(NumberFormatException e){send("-ERR");}}break;
                    case"DELE":{try{int n=Integer.parseInt(arg)-1;del.add(n);send("+OK");}catch(NumberFormatException e){send("-ERR");}}break;
                    case"RSET":del.clear();send("+OK");break;
                    case"NOOP":send("+OK");break;
                    case"QUIT":{for(int i:del)msgs.get(i).delete();send("+OK Bye");return;}
                    default:send("-ERR Unknown");}}}
        catch(IOException e){gui.logError("Client"+id+": "+e.getMessage());}
        finally{gui.onDisconnect(id);try{socket.close();}catch(IOException ignored){}}
    }
    private void send(String m){out.println(m);gui.logServer(id,m);}
    private void load(java.io.File d){msgs.clear();java.io.File[]fs=d.listFiles(f->f.isFile()&&f.getName().endsWith(".txt"));if(fs!=null){java.util.Arrays.sort(fs);msgs.addAll(java.util.Arrays.asList(fs));}}
}

// ─── IMAP embarqué ───────────────────────────────────────────────────────────
class ImapEmbedded extends JPanel {
    private static final Color BG=new Color(20,18,30),PNL=new Color(28,25,42),BDR=new Color(55,48,80);
    private static final Color ACC=new Color(167,139,250),GRN=new Color(74,222,128),RED=new Color(248,113,113);
    private static final Color YLW=new Color(251,191,36),TXT=new Color(230,225,245),DIM=new Color(100,90,130);
    private static final Color CLT=new Color(196,181,253),SRV=new Color(147,197,253);
    private static final Font MONO=new Font("Monospaced",Font.PLAIN,12),BOLD=new Font("SansSerif",Font.BOLD,13);

    private JTextPane logPane;private StyledDocument doc;
    private JLabel statusLbl,connLbl,cmdLbl,seenLbl;
    private JButton startBtn,stopBtn;private JTextField portField;
    private Style sTS,sCLT,sSRV,sINFO,sERR;
    private ServerSocket serverSocket;private volatile boolean running=false;
    private final java.util.concurrent.atomic.AtomicInteger clients=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger cmds=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger seen=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newCachedThreadPool();

    ImapEmbedded(){setBackground(BG);setLayout(new BorderLayout());build();}

    private void build(){
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));top.setBackground(PNL);
        top.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BDR));
        top.add(lbl("Port :"));portField=field("143",6);top.add(portField);
        startBtn=btn("▶ Démarrer",GRN);stopBtn=btn("■ Arrêter",RED);JButton clr=btn("⌫ Effacer",DIM);
        stopBtn.setEnabled(false);
        startBtn.addActionListener(e->start());stopBtn.addActionListener(e->stop());clr.addActionListener(e->clearLog());
        top.add(startBtn);top.add(stopBtn);top.add(clr);
        statusLbl=stat("● Arrêté",RED);connLbl=stat("0",ACC);cmdLbl=stat("0",GRN);seenLbl=stat("0",YLW);
        top.add(Box.createHorizontalStrut(20));
        top.add(statCard(statusLbl,"État"));top.add(statCard(connLbl,"Clients"));top.add(statCard(cmdLbl,"Commandes"));top.add(statCard(seenLbl,"Msgs lus"));
        add(top,BorderLayout.NORTH);
        logPane=new JTextPane();logPane.setEditable(false);logPane.setBackground(new Color(14,12,22));
        logPane.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));logPane.setFont(MONO);doc=logPane.getStyledDocument();
        JScrollPane sc=new JScrollPane(logPane);
        sc.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BDR)," Journal IMAP ",TitledBorder.LEFT,TitledBorder.TOP,BOLD,ACC));
        sc.getViewport().setBackground(new Color(14,12,22));
        JPanel c=new JPanel(new BorderLayout());c.setBackground(BG);c.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));c.add(sc);
        add(c,BorderLayout.CENTER);
        sTS=mkS(DIM,11);sCLT=mkS(CLT,12);sSRV=mkS(SRV,12);sINFO=mkS(YLW,12);sERR=mkS(RED,12);
    }

    private Style mkS(Color c,int sz){Style s=logPane.addStyle("im"+c+sz,null);StyleConstants.setForeground(s,c);StyleConstants.setFontFamily(s,"Monospaced");StyleConstants.setFontSize(s,sz);return s;}
    private void appendLog(String p,String m,Style s){SwingUtilities.invokeLater(()->{try{String ts=new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());doc.insertString(doc.getLength(),"["+ts+"] ",sTS);doc.insertString(doc.getLength(),p+" "+m+"\n",s);logPane.setCaretPosition(doc.getLength());}catch(BadLocationException ignored){}});}
    void logClient(int i,String m){appendLog("Client"+i+" →",m,sCLT);}
    void logServer(int i,String m){appendLog("Serveur →",m,sSRV);}
    void logInfo(String m){appendLog("[INFO]",m,sINFO);}
    void logError(String m){appendLog("[ERREUR]",m,sERR);}
    void clearLog(){try{doc.remove(0,doc.getLength());}catch(BadLocationException ignored){}}
    void onCmd(){int c=cmds.incrementAndGet();SwingUtilities.invokeLater(()->cmdLbl.setText(String.valueOf(c)));}
    void onSeen(){int c=seen.incrementAndGet();SwingUtilities.invokeLater(()->seenLbl.setText(String.valueOf(c)));}
    void onDisconnect(int id){clients.decrementAndGet();SwingUtilities.invokeLater(()->connLbl.setText(String.valueOf(clients.get())));logInfo("Client"+id+" déconnecté.");}

    private void start(){
        int port;try{port=Integer.parseInt(portField.getText().trim());}catch(NumberFormatException e){JOptionPane.showMessageDialog(null,"Port invalide.");return;}
        try{serverSocket=new ServerSocket(port);running=true;startBtn.setEnabled(false);stopBtn.setEnabled(true);portField.setEnabled(false);
            statusLbl.setText("● Actif");statusLbl.setForeground(GRN);logInfo("Serveur IMAP démarré sur le port "+port);
            Thread t=new Thread(()->{while(running){try{Socket c=serverSocket.accept();int id=clients.incrementAndGet();SwingUtilities.invokeLater(()->connLbl.setText(String.valueOf(clients.get())));logInfo("Connexion "+c.getInetAddress()+" (Client"+id+")");pool.submit(new ImapSessionEmbed(c,id,this));}catch(IOException e){if(running)logError(e.getMessage());}}});t.setDaemon(true);t.start();
        }catch(IOException e){logError("Démarrage impossible: "+e.getMessage());}
    }
    private void stop(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(IOException ignored){}pool.shutdownNow();startBtn.setEnabled(true);stopBtn.setEnabled(false);portField.setEnabled(true);statusLbl.setText("● Arrêté");statusLbl.setForeground(RED);logInfo("Serveur IMAP arrêté.");}

    private JLabel lbl(String t){JLabel l=new JLabel(t);l.setForeground(DIM);l.setFont(new Font("SansSerif",Font.PLAIN,12));return l;}
    private JTextField field(String v,int c){JTextField f=new JTextField(v,c);f.setBackground(BG);f.setForeground(TXT);f.setCaretColor(TXT);f.setBorder(BorderFactory.createLineBorder(BDR));f.setFont(MONO);return f;}
    private JButton btn(String t,Color c){JButton b=new JButton(t);b.setFont(BOLD);b.setForeground(BG);b.setBackground(c);b.setFocusPainted(false);b.setBorderPainted(false);b.setBorder(BorderFactory.createEmptyBorder(5,12,5,12));return b;}
    private JLabel stat(String t,Color c){JLabel l=new JLabel(t,SwingConstants.CENTER);l.setFont(BOLD);l.setForeground(c);return l;}
    private JPanel statCard(JLabel l,String cap){JPanel p=new JPanel(new BorderLayout());p.setBackground(PNL);p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BDR),BorderFactory.createEmptyBorder(4,10,4,10)));JLabel c=new JLabel(cap,SwingConstants.CENTER);c.setFont(new Font("SansSerif",Font.PLAIN,10));c.setForeground(DIM);p.add(l,BorderLayout.CENTER);p.add(c,BorderLayout.SOUTH);return p;}
}

class ImapSessionEmbed implements Runnable {
    private final Socket socket;private final int id;private final ImapEmbedded gui;
    private BufferedReader in;private PrintWriter out;
    private enum S{NA,AUTH,SEL}private S state=S.NA;private String user;
    private java.util.List<java.io.File> inbox=new java.util.ArrayList<>();
    private java.util.Map<Integer,Boolean> seenMap=new java.util.HashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger uid=new java.util.concurrent.atomic.AtomicInteger(1);
    private static final String ROOT="mailserver";
    ImapSessionEmbed(Socket s, int id, ImapEmbedded gui){this.socket=s;this.id=id;this.gui=gui;}
    @Override public void run(){
        try{in=new BufferedReader(new InputStreamReader(socket.getInputStream()));out=new PrintWriter(socket.getOutputStream(),true);send("* OK IMAP ready");
            String line;while((line=in.readLine())!=null){if(line.trim().isEmpty())continue;gui.logClient(id,line);gui.onCmd();
                String[]p=line.split(" ",3);if(p.length<2)continue;
                String tag=p[0];String cmd=p[1].toUpperCase();String args=p.length>2?p[2]:"";
                switch(cmd){
                    case"LOGIN":{if(state!=S.NA){send(tag+" BAD Already auth");break;}String[]a=args.split(" ");if(a.length<2){send(tag+" BAD LOGIN req u/p");break;}user=a[0];state=S.AUTH;send(tag+" OK LOGIN completed");break;}
                    case"SELECT":{if(state!=S.AUTH){send(tag+" BAD SELECT not allowed");break;}if(!args.trim().equalsIgnoreCase("INBOX")){send(tag+" NO Folder not found");break;}loadInbox();state=S.SEL;send("* FLAGS (\\Seen)");send("* "+inbox.size()+" EXISTS");send("* 0 RECENT");send(tag+" OK [READ-WRITE] SELECT completed");break;}
                    case"FETCH":{if(state!=S.SEL){send(tag+" BAD FETCH not allowed");break;}String[]a=args.split(" ",2);try{int n=Integer.parseInt(a[0])-1;if(n<0||n>=inbox.size())throw new NumberFormatException();
                        if(a.length>1&&a[1].equalsIgnoreCase("BODY[HEADER]")){String h=header(inbox.get(n));send("* "+(n+1)+" FETCH (BODY[HEADER] {"+h.length()+"})");out.println(h);}
                        else{String f=full(inbox.get(n));send("* "+(n+1)+" FETCH (RFC822 {"+f.length()+"})");out.println(f);seenMap.put(n,true);}
                        send(tag+" OK FETCH completed");}catch(NumberFormatException e){send(tag+" BAD Invalid msg num");}catch(IOException e){send(tag+" BAD Read error");}break;}
                    case"STORE":{if(state!=S.SEL){send(tag+" BAD STORE not allowed");break;}String[]a=args.split(" ",3);try{int n=Integer.parseInt(a[0])-1;if(a.length>2&&a[2].contains("\\Seen")){seenMap.put(n,true);gui.onSeen();}send("* "+(n+1)+" FETCH (FLAGS (\\Seen))");send(tag+" OK STORE completed");}catch(NumberFormatException e){send(tag+" BAD Invalid msg num");}break;}
                    case"SEARCH":{if(state!=S.SEL){send(tag+" BAD SEARCH not allowed");break;}String kw=args.replace("\"","").toLowerCase();StringBuilder res=new StringBuilder("* SEARCH");for(int i=0;i<inbox.size();i++)try{if(full(inbox.get(i)).toLowerCase().contains(kw))res.append(" ").append(i+1);}catch(IOException ignored){}send(res.toString());send(tag+" OK SEARCH completed");break;}
                    case"LOGOUT":{send("* BYE Logging out");send(tag+" OK LOGOUT");return;}
                    default:send(tag+" BAD Unknown command");}}}
        catch(IOException e){gui.logError("Client"+id+": "+e.getMessage());}
        finally{gui.onDisconnect(id);try{socket.close();}catch(IOException ignored){}}
    }
    private void send(String m){out.println(m);gui.logServer(id,m);}
    private void loadInbox(){inbox.clear();java.io.File dir=new java.io.File(ROOT+java.io.File.separator+user+java.io.File.separator+"INBOX");if(!dir.exists())dir.mkdirs();java.io.File[]fs=dir.listFiles();if(fs!=null){java.util.Arrays.sort(fs);inbox.addAll(java.util.Arrays.asList(fs));}}
    private String header(java.io.File f)throws IOException {BufferedReader r=new BufferedReader(new java.io.FileReader(f));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null){if(l.isEmpty())break;sb.append(l).append("\r\n");}r.close();return sb.toString();}
    private String full(java.io.File f)throws IOException{BufferedReader r=new BufferedReader(new java.io.FileReader(f));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l).append("\r\n");r.close();return sb.toString();}
}
package org.example;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface graphique de supervision du serveur IMAP.
 * Partie 3 du TP – Interfaces de Supervision pour la Distribution des Serveurs de Messagerie
 */
public class ImapServerGUI extends JFrame {

    // ── Couleurs du thème ──────────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(18, 22, 30);
    private static final Color BG_PANEL     = new Color(26, 32, 44);
    private static final Color BG_CARD      = new Color(34, 42, 58);
    private static final Color ACCENT_IMAP  = new Color(167, 139, 250);  // violet IMAP
    private static final Color TEXT_PRIMARY = new Color(236, 240, 245);
    private static final Color TEXT_MUTED   = new Color(120, 140, 165);
    private static final Color SUCCESS      = new Color(72, 199, 142);
    private static final Color DANGER       = new Color(240, 82, 82);
    private static final Color CLIENT_COLOR = new Color(100, 200, 255);
    private static final Color SERVER_COLOR = new Color(200, 170, 255);

    private static final int PORT = 143; // port non-privilégié

    // ── État ───────────────────────────────────────────────────────────────
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private final AtomicInteger totalFetched     = new AtomicInteger(0);
    private final AtomicInteger totalSearches    = new AtomicInteger(0);

    // ── Composants UI ─────────────────────────────────────────────────────
    private JTextPane   logPane;
    private StyledDocument logDoc;
    private JLabel      statusLabel, clientCountLabel, fetchedLabel, searchLabel;
    private JButton     startBtn, stopBtn, clearBtn;
    private JProgressBar activityBar;

    public ImapServerGUI() {
        super("IMAP Server — Supervision");
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 660);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(BG_PANEL);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_IMAP));
        h.setPreferredSize(new Dimension(0, 64));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        left.setOpaque(false);
        JLabel icon = new JLabel("📨"); icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26)); icon.setForeground(ACCENT_IMAP);
        JLabel title = new JLabel("IMAP Server"); title.setFont(new Font("Consolas", Font.BOLD, 20)); title.setForeground(TEXT_PRIMARY);
        JLabel badge = new JLabel("PORT " + PORT);
        badge.setFont(new Font("Consolas", Font.BOLD, 11)); badge.setForeground(BG_DARK);
        badge.setBackground(ACCENT_IMAP); badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        // Badge RFC
        JLabel rfcBadge = new JLabel("RFC 9051");
        rfcBadge.setFont(new Font("Consolas", Font.BOLD, 11)); rfcBadge.setForeground(ACCENT_IMAP);
        rfcBadge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_IMAP, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        left.add(icon); left.add(title); left.add(badge); left.add(rfcBadge);
        h.add(left, BorderLayout.WEST);
        h.add(buildControls(), BorderLayout.EAST);
        return h;
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
                g2.dispose(); super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Consolas", Font.BOLD, 12)); btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(130, 32));
        return btn;
    }

    private JPanel buildCenter() {
        JPanel c = new JPanel(new BorderLayout(10, 10));
        c.setBackground(BG_DARK);
        c.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildMetrics(), buildLogPanel());
        split.setDividerLocation(210);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(BG_DARK);
        split.setDividerSize(4);

        c.add(split, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildMetrics() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK);
        p.setPreferredSize(new Dimension(200, 0));

        p.add(buildCard("STATUT",    "ARRÊTÉ", DANGER,      "status"));
        p.add(Box.createVerticalStrut(8));
        p.add(buildCard("CLIENTS",   "0",      ACCENT_IMAP, "clients"));
        p.add(Box.createVerticalStrut(8));
        p.add(buildCard("FETCH",     "0",      SUCCESS,     "fetched"));
        p.add(Box.createVerticalStrut(8));
        p.add(buildCard("SEARCH",    "0",      new Color(255, 210, 80), "search"));
        p.add(Box.createVerticalStrut(8));

        // Légende états IMAP
        JPanel stateCard = new JPanel();
        stateCard.setLayout(new BoxLayout(stateCard, BoxLayout.Y_AXIS));
        stateCard.setBackground(BG_CARD);
        stateCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_IMAP.darker(), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        stateCard.setMaximumSize(new Dimension(200, 130));
        JLabel stTitle = new JLabel("ÉTATS IMAP"); stTitle.setFont(new Font("Consolas",Font.BOLD,10)); stTitle.setForeground(TEXT_MUTED);
        stateCard.add(stTitle);
        stateCard.add(Box.createVerticalStrut(6));
        stateCard.add(stateRow("NOT_AUTH", new Color(240,150,80)));
        stateCard.add(stateRow("AUTH",     new Color(100,230,100)));
        stateCard.add(stateRow("SELECTED", ACCENT_IMAP));
        stateCard.add(stateRow("LOGOUT",   DANGER));
        p.add(stateCard);
        p.add(Box.createVerticalStrut(8));

        // Barre activité
        JPanel actCard = new JPanel(new BorderLayout(4,4));
        actCard.setBackground(BG_CARD);
        actCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_IMAP.darker(),1,true),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        JLabel actTitle2 = new JLabel("ACTIVITÉ"); actTitle2.setFont(new Font("Consolas",Font.BOLD,10)); actTitle2.setForeground(TEXT_MUTED);
        activityBar = new JProgressBar(0,100);
        activityBar.setBackground(BG_DARK); activityBar.setForeground(ACCENT_IMAP); activityBar.setBorderPainted(false);
        actCard.add(actTitle2,BorderLayout.NORTH); actCard.add(activityBar,BorderLayout.CENTER);
        p.add(actCard);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel stateRow(String name, Color c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setOpaque(false);
        JLabel dot = new JLabel("●"); dot.setForeground(c); dot.setFont(new Font("Consolas",Font.PLAIN,10));
        JLabel lbl = new JLabel(name); lbl.setForeground(TEXT_MUTED); lbl.setFont(new Font("Consolas",Font.PLAIN,10));
        row.add(dot); row.add(lbl);
        return row;
    }

    private JPanel buildCard(String title, String value, Color color, String id) {
        JPanel card = new JPanel(new BorderLayout(4,4));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color.darker(),1,true),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        card.setMaximumSize(new Dimension(200,80));
        JLabel t = new JLabel(title); t.setFont(new Font("Consolas",Font.BOLD,10)); t.setForeground(TEXT_MUTED);
        JLabel v = new JLabel(value); v.setFont(new Font("Consolas",Font.BOLD,22)); v.setForeground(color);
        card.add(t,BorderLayout.NORTH); card.add(v,BorderLayout.CENTER);
        switch(id){
            case "status":  statusLabel       = v; break;
            case "clients": clientCountLabel  = v; break;
            case "fetched": fetchedLabel      = v; break;
            case "search":  searchLabel       = v; break;
        }
        return card;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_CARD);
        p.setBorder(new LineBorder(ACCENT_IMAP.darker(), 1, true));

        // En-tête avec légende couleurs
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(BG_PANEL);
        headerRow.setPreferredSize(new Dimension(0, 34));
        JLabel logTitle = new JLabel("  📋 Historique des commandes");
        logTitle.setFont(new Font("Consolas",Font.BOLD,13)); logTitle.setForeground(ACCENT_IMAP);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        legend.setOpaque(false);
        legend.add(legendDot(CLIENT_COLOR, "Client"));
        legend.add(legendDot(SERVER_COLOR, "Serveur"));

        headerRow.add(logTitle, BorderLayout.WEST);
        headerRow.add(legend, BorderLayout.EAST);
        p.add(headerRow, BorderLayout.NORTH);

        logPane = new JTextPane(); logPane.setEditable(false);
        logPane.setBackground(new Color(12,15,22)); logPane.setFont(new Font("Consolas",Font.PLAIN,13));
        logDoc = logPane.getStyledDocument();
        JScrollPane scroll = new JScrollPane(logPane); scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel legendDot(Color c, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        row.setOpaque(false);
        JLabel dot = new JLabel("●"); dot.setForeground(c); dot.setFont(new Font("Consolas",Font.PLAIN,11));
        JLabel lbl = new JLabel(label); lbl.setForeground(TEXT_MUTED); lbl.setFont(new Font("Consolas",Font.PLAIN,10));
        row.add(dot); row.add(lbl);
        return row;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, ACCENT_IMAP.darker()));
        JLabel portLbl = new JLabel("Port : "+PORT); portLbl.setFont(new Font("Consolas",Font.PLAIN,11)); portLbl.setForeground(TEXT_MUTED);
        JLabel proto = new JLabel("Protocole : IMAP4rev2 / RFC 9051"); proto.setFont(new Font("Consolas",Font.PLAIN,11)); proto.setForeground(TEXT_MUTED);
        JLabel sep = new JLabel("  |  "); sep.setForeground(TEXT_MUTED);
        bar.add(portLbl); bar.add(sep); bar.add(proto);
        return bar;
    }

    // ── Serveur ────────────────────────────────────────────────────────────
    private void startServer() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(PORT);
            running = true; updateStatus(true);
            log("SYSTÈME","Serveur IMAP démarré sur le port "+PORT, SUCCESS);
            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        connectedClients.incrementAndGet();
                        SwingUtilities.invokeLater(()->clientCountLabel.setText(String.valueOf(connectedClients.get())));
                        log("CONNEXION","Nouveau client : "+client.getInetAddress().getHostAddress(), CLIENT_COLOR);
                        new GUIImapSession(client, this).start();
                    } catch (IOException e) { if (running) log("ERREUR",e.getMessage(),DANGER); }
                }
            });
            acceptThread.setDaemon(true); acceptThread.start();
            pulseActivity();
        } catch (IOException e) { log("ERREUR","Impossible de démarrer : "+e.getMessage(),DANGER); }
    }

    private void stopServer() {
        if (!running) return;
        running = false;
        try { if (serverSocket!=null) serverSocket.close(); } catch (IOException ignored) {}
        updateStatus(false);
        log("SYSTÈME","Serveur IMAP arrêté.", DANGER);
    }

    private void updateStatus(boolean online) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(online ? "EN LIGNE" : "ARRÊTÉ");
            statusLabel.setForeground(online ? SUCCESS : DANGER);
            startBtn.setEnabled(!online); stopBtn.setEnabled(online);
        });
    }

    void onFetch()   { int n=totalFetched.incrementAndGet();  SwingUtilities.invokeLater(()->fetchedLabel.setText(String.valueOf(n))); }
    void onSearch()  { int n=totalSearches.incrementAndGet(); SwingUtilities.invokeLater(()->searchLabel.setText(String.valueOf(n))); }
    void onClientDisconnected() { connectedClients.decrementAndGet(); SwingUtilities.invokeLater(()->clientCountLabel.setText(String.valueOf(connectedClients.get()))); }

    private void pulseActivity() {
        Timer t = new Timer(80, null); int[] v={0}; boolean[] up={true};
        t.addActionListener(e -> {
            if(!running){activityBar.setValue(0);t.stop();return;}
            if(up[0]){v[0]+=5;if(v[0]>=100)up[0]=false;}else{v[0]-=5;if(v[0]<=0)up[0]=true;}
            activityBar.setValue(v[0]);
        });
        t.start();
    }

    void log(String who, String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                Style s1=logPane.addStyle("t",null); StyleConstants.setForeground(s1,TEXT_MUTED); StyleConstants.setFontFamily(s1,"Consolas"); StyleConstants.setFontSize(s1,12);
                logDoc.insertString(logDoc.getLength(),"["+time+"] ",s1);
                Style s2=logPane.addStyle("w",null); StyleConstants.setForeground(s2,color); StyleConstants.setBold(s2,true); StyleConstants.setFontFamily(s2,"Consolas"); StyleConstants.setFontSize(s2,12);
                logDoc.insertString(logDoc.getLength(),who+" → ",s2);
                Style s3=logPane.addStyle("m",null); StyleConstants.setForeground(s3,TEXT_PRIMARY); StyleConstants.setFontFamily(s3,"Consolas"); StyleConstants.setFontSize(s3,12);
                logDoc.insertString(logDoc.getLength(),message+"\n",s3);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void clearLog() { try{logDoc.remove(0,logDoc.getLength());}catch(BadLocationException ignored){} }

    public static void main(String[] args) {
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        SwingUtilities.invokeLater(ImapServerGUI::new);
    }
}

// ── Session IMAP instrumentée ──────────────────────────────────────────────
class GUIImapSession extends Thread {
    private final Socket socket;
    private final ImapServerGUI gui;
    private BufferedReader in; private PrintWriter out;

    private enum State { NOT_AUTHENTICATED, AUTHENTICATED, SELECTED }
    private State state = State.NOT_AUTHENTICATED;
    private String username;
    private List<GUIImapMessage> inbox = new ArrayList<>();
    private AtomicInteger uidCounter = new AtomicInteger(1);

    private static final String MAIL_ROOT = "mailserver";
    private static final Color SRV = new Color(200, 170, 255);
    private static final Color CLI = new Color(100, 200, 255);

    GUIImapSession(Socket s, ImapServerGUI g){socket=s;gui=g;}

    private void send(String r){out.println(r);out.flush();gui.log("Serveur",r,SRV);}

    @Override public void run(){
        String addr=socket.getInetAddress().getHostAddress();
        try{
            in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out=new PrintWriter(socket.getOutputStream(),true);
            send("* OK IMAP server ready");

            String line;
            while((line=in.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                gui.log("Client["+addr+"]",line,CLI);
                String[] parts=line.split(" ",3);
                String tag=parts[0], cmd=parts[1].toUpperCase();
                String args=parts.length>2?parts[2]:"";

                switch(cmd){
                    case "LOGIN":  handleLogin(tag,args); break;
                    case "SELECT": handleSelect(tag,args); break;
                    case "FETCH":  handleFetch(tag,args); break;
                    case "STORE":  handleStore(tag,args); break;
                    case "SEARCH": handleSearch(tag,args); break;
                    case "LIST":   handleList(tag,args); break;
                    case "LOGOUT": handleLogout(tag); return;
                    default: send(tag+" BAD Unknown command");
                }
            }
        }catch(IOException e){gui.log("ERREUR",e.getMessage(),new Color(240,82,82));}
        finally{try{socket.close();}catch(IOException ignored){}gui.onClientDisconnected();gui.log("DÉCONNEXION","Client "+addr+" déconnecté",new Color(120,140,165));}
    }

    private void handleLogin(String tag,String args){
        if(state!=State.NOT_AUTHENTICATED){send(tag+" BAD Already authenticated");return;}
        String[] p=args.split(" ");
        if(p.length<2){send(tag+" BAD LOGIN requires username and password");return;}
        username=p[0]; state=State.AUTHENTICATED;
        gui.log("AUTH","Utilisateur authentifié : "+username, new Color(72,199,142));
        send(tag+" OK LOGIN completed");
    }

    private void handleSelect(String tag,String folder){
        if(state!=State.AUTHENTICATED){send(tag+" BAD SELECT not allowed in current state");return;}
        if(!folder.trim().equalsIgnoreCase("INBOX")){send(tag+" NO Folder does not exist");return;}
        state=State.SELECTED; loadInbox();
        send("* FLAGS (\\Seen)");
        send("* "+inbox.size()+" EXISTS");
        send("* 0 RECENT");
        gui.log("SELECT","INBOX sélectionnée, "+inbox.size()+" messages", new Color(167,139,250));
        send(tag+" OK [READ-WRITE] SELECT completed");
    }

    private void handleFetch(String tag,String args){
        if(state!=State.SELECTED){send(tag+" BAD FETCH not allowed in current state");return;}
        String[] p=args.split(" ",2);
        int n;
        try{n=Integer.parseInt(p[0])-1;if(n<0||n>=inbox.size())throw new NumberFormatException();}
        catch(NumberFormatException e){send(tag+" BAD Invalid message number");return;}
        GUIImapMessage msg=inbox.get(n);
        try{
            if(p.length>1&&p[1].equalsIgnoreCase("BODY[HEADER]")){
                send("* "+(n+1)+" FETCH (FLAGS (\\Seen) BODY[HEADER] {"+msg.getHeader().length()+"}");
                send(msg.getHeader()+")");
            } else {
                send("* "+(n+1)+" FETCH (FLAGS (\\Seen) RFC822 {"+msg.getFull().length()+"}");
                send(msg.getFull()+")");
                msg.seen=true;
            }
            gui.onFetch();
            send(tag+" OK FETCH completed");
        }catch(IOException e){send(tag+" BAD Error reading message");}
    }

    private void handleStore(String tag,String args){
        if(state!=State.SELECTED){send(tag+" BAD STORE not allowed in current state");return;}
        String[] p=args.split(" ",3);
        int n;
        try{n=Integer.parseInt(p[0])-1;if(n<0||n>=inbox.size())throw new NumberFormatException();}
        catch(NumberFormatException e){send(tag+" BAD Invalid message number");return;}
        if(p.length>2&&p[2].contains("\\Seen")) inbox.get(n).seen=true;
        send("* "+(n+1)+" FETCH (FLAGS (\\Seen))");
        send(tag+" OK STORE completed");
    }

    private void handleSearch(String tag,String args){
        if(state!=State.SELECTED){send(tag+" BAD SEARCH not allowed in current state");return;}
        List<Integer> results=new ArrayList<>();
        String kw=args.replace("\"","").toLowerCase();
        for(int i=0;i<inbox.size();i++){
            try{if(inbox.get(i).getFull().toLowerCase().contains(kw))results.add(i+1);}catch(IOException ignored){}
        }
        StringBuilder sb=new StringBuilder("* SEARCH");
        for(int r:results) sb.append(" ").append(r);
        send(sb.toString());
        gui.onSearch();
        gui.log("SEARCH","Recherche '"+kw+"' → "+results.size()+" résultat(s)", new Color(255,210,80));
        send(tag+" OK SEARCH completed");
    }

    private void handleList(String tag,String args){
        // Réponse minimale LIST pour compatibilité clients
        send("* LIST (\\HasNoChildren) \".\" \"INBOX\"");
        send(tag+" OK LIST completed");
    }

    private void handleLogout(String tag){
        send("* BYE IMAP server logging out");
        send(tag+" OK LOGOUT completed");
    }

    private void loadInbox(){
        inbox.clear();
        File dir=new File(MAIL_ROOT+File.separator+username+File.separator+"INBOX");
        if(!dir.exists()){ dir.mkdirs();
            // Fallback : chercher dans mailserver/username directement
            dir=new File(MAIL_ROOT+File.separator+username);
        }
        File[] files=dir.listFiles();
        if(files!=null){ Arrays.sort(files); for(File f:files) inbox.add(new GUIImapMessage(uidCounter.getAndIncrement(),f)); }
    }
}

// ── Modèle message IMAP ────────────────────────────────────────────────────
class GUIImapMessage {
    int uid; boolean seen; File file;
    GUIImapMessage(int uid, File file){ this.uid=uid; this.file=file; }

    String getHeader() throws IOException {
        BufferedReader r=new BufferedReader(new FileReader(file));
        StringBuilder sb=new StringBuilder(); String l;
        while((l=r.readLine())!=null){ if(l.isEmpty()) break; sb.append(l).append("\r\n"); }
        r.close(); return sb.toString();
    }
    String getFull() throws IOException {
        BufferedReader r=new BufferedReader(new FileReader(file));
        StringBuilder sb=new StringBuilder(); String l;
        while((l=r.readLine())!=null) sb.append(l).append("\r\n");
        r.close(); return sb.toString();
    }
}
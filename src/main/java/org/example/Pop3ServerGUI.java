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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface graphique de supervision du serveur POP3.
 * Partie 3 du TP – Interfaces de Supervision pour la Distribution des Serveurs de Messagerie
 */
public class Pop3ServerGUI extends JFrame {

    // ── Couleurs du thème ──────────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(18, 22, 30);
    private static final Color BG_PANEL     = new Color(26, 32, 44);
    private static final Color BG_CARD      = new Color(34, 42, 58);
    private static final Color ACCENT_POP3  = new Color(56, 189, 248);   // cyan POP3
    private static final Color TEXT_PRIMARY = new Color(236, 240, 245);
    private static final Color TEXT_MUTED   = new Color(120, 140, 165);
    private static final Color SUCCESS      = new Color(72, 199, 142);
    private static final Color DANGER       = new Color(240, 82, 82);

    private static final int PORT = 110;

    // ── État ───────────────────────────────────────────────────────────────
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private final AtomicInteger totalRetrieved   = new AtomicInteger(0);
    private final AtomicInteger totalDeleted     = new AtomicInteger(0);

    // ── Composants UI ─────────────────────────────────────────────────────
    private JTextPane   logPane;
    private StyledDocument logDoc;
    private JLabel      statusLabel, clientCountLabel, retrievedLabel, deletedLabel;
    private JButton     startBtn, stopBtn, clearBtn;
    private JProgressBar activityBar;

    public Pop3ServerGUI() {
        super("POP3 Server — Supervision");
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 640);
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
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_POP3));
        h.setPreferredSize(new Dimension(0, 64));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        left.setOpaque(false);
        JLabel icon = new JLabel("📥"); icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26)); icon.setForeground(ACCENT_POP3);
        JLabel title = new JLabel("POP3 Server"); title.setFont(new Font("Consolas", Font.BOLD, 20)); title.setForeground(TEXT_PRIMARY);
        JLabel badge = new JLabel("PORT " + PORT);
        badge.setFont(new Font("Consolas", Font.BOLD, 11)); badge.setForeground(BG_DARK);
        badge.setBackground(ACCENT_POP3); badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        left.add(icon); left.add(title); left.add(badge);
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
        c.add(buildMetrics(), BorderLayout.WEST);
        c.add(buildLogPanel(), BorderLayout.CENTER);
        return c;
    }

    private JPanel buildMetrics() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK);
        p.setPreferredSize(new Dimension(200, 0));

        p.add(buildCard("STATUT",    "ARRÊTÉ", DANGER,      true,  false, false));
        p.add(Box.createVerticalStrut(8));
        p.add(buildCard("CLIENTS",   "0",      ACCENT_POP3, false, true,  false));
        p.add(Box.createVerticalStrut(8));
        p.add(buildCard("RÉCUPÉRÉS", "0",      SUCCESS,     false, false, true));
        p.add(Box.createVerticalStrut(8));

        // Carte supprimés
        JPanel delCard = new JPanel(new BorderLayout(4,4));
        delCard.setBackground(BG_CARD);
        delCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(DANGER.darker(), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        delCard.setMaximumSize(new Dimension(200, 80));
        JLabel delTitle = new JLabel("SUPPRIMÉS"); delTitle.setFont(new Font("Consolas",Font.BOLD,10)); delTitle.setForeground(TEXT_MUTED);
        deletedLabel = new JLabel("0"); deletedLabel.setFont(new Font("Consolas",Font.BOLD,22)); deletedLabel.setForeground(DANGER);
        delCard.add(delTitle, BorderLayout.NORTH); delCard.add(deletedLabel, BorderLayout.CENTER);
        p.add(delCard);
        p.add(Box.createVerticalStrut(8));

        // Barre activité
        JPanel actCard = new JPanel(new BorderLayout(4,4));
        actCard.setBackground(BG_CARD);
        actCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_POP3.darker(), 1, true),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        JLabel actTitle = new JLabel("ACTIVITÉ"); actTitle.setFont(new Font("Consolas",Font.BOLD,10)); actTitle.setForeground(TEXT_MUTED);
        activityBar = new JProgressBar(0,100);
        activityBar.setBackground(BG_DARK); activityBar.setForeground(ACCENT_POP3); activityBar.setBorderPainted(false);
        actCard.add(actTitle,BorderLayout.NORTH); actCard.add(activityBar,BorderLayout.CENTER);
        p.add(actCard);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildCard(String title, String value, Color color, boolean isSt, boolean isCl, boolean isRt) {
        JPanel card = new JPanel(new BorderLayout(4,4));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color.darker(),1,true),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        card.setMaximumSize(new Dimension(200,80));
        JLabel t = new JLabel(title); t.setFont(new Font("Consolas",Font.BOLD,10)); t.setForeground(TEXT_MUTED);
        JLabel v = new JLabel(value); v.setFont(new Font("Consolas",Font.BOLD,22)); v.setForeground(color);
        card.add(t,BorderLayout.NORTH); card.add(v,BorderLayout.CENTER);
        if (isSt) statusLabel = v;
        if (isCl) clientCountLabel = v;
        if (isRt) retrievedLabel = v;
        return card;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_CARD);
        p.setBorder(new LineBorder(ACCENT_POP3.darker(), 1, true));
        JLabel logTitle = new JLabel("  📋 Historique des commandes");
        logTitle.setFont(new Font("Consolas",Font.BOLD,13)); logTitle.setForeground(ACCENT_POP3);
        logTitle.setBackground(BG_PANEL); logTitle.setOpaque(true); logTitle.setPreferredSize(new Dimension(0,34));
        p.add(logTitle, BorderLayout.NORTH);
        logPane = new JTextPane(); logPane.setEditable(false);
        logPane.setBackground(new Color(12,15,22)); logPane.setFont(new Font("Consolas",Font.PLAIN,13));
        logDoc = logPane.getStyledDocument();
        JScrollPane scroll = new JScrollPane(logPane); scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(1,0,0,0, ACCENT_POP3.darker()));
        JLabel portLbl = new JLabel("Port : " + PORT); portLbl.setFont(new Font("Consolas",Font.PLAIN,11)); portLbl.setForeground(TEXT_MUTED);
        JLabel proto = new JLabel("Protocole : POP3 / RFC 1939"); proto.setFont(new Font("Consolas",Font.PLAIN,11)); proto.setForeground(TEXT_MUTED);
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
            log("SYSTÈME","Serveur POP3 démarré sur le port " + PORT, SUCCESS);
            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        connectedClients.incrementAndGet();
                        SwingUtilities.invokeLater(() -> clientCountLabel.setText(String.valueOf(connectedClients.get())));
                        log("CONNEXION","Nouveau client : "+client.getInetAddress().getHostAddress(), ACCENT_POP3);
                        new GUIPop3Session(client, this).start();
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
        log("SYSTÈME","Serveur POP3 arrêté.", DANGER);
    }

    private void updateStatus(boolean online) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(online ? "EN LIGNE" : "ARRÊTÉ");
            statusLabel.setForeground(online ? SUCCESS : DANGER);
            startBtn.setEnabled(!online); stopBtn.setEnabled(online);
        });
    }

    void onMessageRetrieved() { int n=totalRetrieved.incrementAndGet(); SwingUtilities.invokeLater(()->retrievedLabel.setText(String.valueOf(n))); }
    void onMessageDeleted()   { int n=totalDeleted.incrementAndGet();   SwingUtilities.invokeLater(()->deletedLabel.setText(String.valueOf(n))); }
    void onClientDisconnected() { connectedClients.decrementAndGet(); SwingUtilities.invokeLater(()->clientCountLabel.setText(String.valueOf(connectedClients.get()))); }

    private void pulseActivity() {
        Timer t = new Timer(80, null); int[] val={0}; boolean[] up={true};
        t.addActionListener(e -> {
            if(!running){activityBar.setValue(0);t.stop();return;}
            if(up[0]){val[0]+=5;if(val[0]>=100)up[0]=false;}else{val[0]-=5;if(val[0]<=0)up[0]=true;}
            activityBar.setValue(val[0]);
        });
        t.start();
    }

    void log(String who, String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                Style s1 = logPane.addStyle("t",null); StyleConstants.setForeground(s1,TEXT_MUTED); StyleConstants.setFontFamily(s1,"Consolas"); StyleConstants.setFontSize(s1,12);
                logDoc.insertString(logDoc.getLength(),"["+time+"] ",s1);
                Style s2 = logPane.addStyle("w",null); StyleConstants.setForeground(s2,color); StyleConstants.setBold(s2,true); StyleConstants.setFontFamily(s2,"Consolas"); StyleConstants.setFontSize(s2,12);
                logDoc.insertString(logDoc.getLength(),who+" → ",s2);
                Style s3 = logPane.addStyle("m",null); StyleConstants.setForeground(s3,TEXT_PRIMARY); StyleConstants.setFontFamily(s3,"Consolas"); StyleConstants.setFontSize(s3,12);
                logDoc.insertString(logDoc.getLength(),message+"\n",s3);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void clearLog() { try{logDoc.remove(0,logDoc.getLength());}catch(BadLocationException ignored){} }

    public static void main(String[] args) {
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        SwingUtilities.invokeLater(Pop3ServerGUI::new);
    }
}

// ── Session POP3 instrumentée ──────────────────────────────────────────────
class GUIPop3Session extends Thread {
    private final Socket socket;
    private final Pop3ServerGUI gui;
    private BufferedReader in; private PrintWriter out;
    private String username; private boolean authenticated=false;
    private java.util.List<File> messages = new ArrayList<>();
    private java.util.Set<Integer> deleted = new java.util.HashSet<>();
    private static final String MAIL_ROOT = "mailserver";
    private static final Color SRV = new Color(100,230,200);
    private static final Color CLI = new Color(100,200,255);

    GUIPop3Session(Socket s, Pop3ServerGUI g){socket=s;gui=g;}

    private void send(String r){out.print(r+"\r\n");out.flush();gui.log("Serveur",r,SRV);}

    @Override public void run(){
        String addr=socket.getInetAddress().getHostAddress();
        try{
            in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out=new PrintWriter(socket.getOutputStream(),false);
            send("+OK POP3 server ready");
            String line;
            while((line=in.readLine())!=null){
                gui.log("Client["+addr+"]",line,CLI);
                String[] p=line.split(" ",2);
                String cmd=p[0].toUpperCase();
                String arg=p.length>1?p[1].trim():"";
                switch(cmd){
                    case "USER": handleUser(arg); break;
                    case "PASS": handlePass(arg); break;
                    case "STAT": handleStat(); break;
                    case "LIST": handleList(arg); break;
                    case "RETR": handleRetr(arg); break;
                    case "DELE": handleDele(arg); break;
                    case "NOOP": send("+OK"); break;
                    case "RSET": deleted.clear(); send("+OK"); break;
                    case "QUIT": handleQuit(); return;
                    default: send("-ERR Unknown command");
                }
            }
        }catch(IOException e){gui.log("ERREUR",e.getMessage(),new Color(240,82,82));}
        finally{try{socket.close();}catch(IOException ignored){}gui.onClientDisconnected();gui.log("DÉCONNEXION","Client "+addr+" déconnecté",new Color(120,140,165));}
    }

    private void handleUser(String u){if(u.isEmpty()){send("-ERR Username required");return;}username=u;send("+OK User accepted");}
    private void handlePass(String p){
        if(username==null){send("-ERR Send USER first");return;}
        File dir=new File(MAIL_ROOT+File.separator+username);
        if(!dir.exists()||!dir.isDirectory()){send("-ERR Authentication failed");return;}
        authenticated=true; loadMessages();
        send("+OK Mailbox open, "+messages.size()+" messages");
    }
    private void loadMessages(){messages.clear();File dir=new File(MAIL_ROOT+File.separator+username);File[] fs=dir.listFiles();if(fs!=null){Arrays.sort(fs);for(File f:fs)messages.add(f);}}
    private void handleStat(){if(!authenticated){send("-ERR Not authenticated");return;}long sz=0;int n=0;for(int i=0;i<messages.size();i++)if(!deleted.contains(i)){sz+=messages.get(i).length();n++;}send("+OK "+n+" "+sz);}
    private void handleList(String arg){
        if(!authenticated){send("-ERR Not authenticated");return;}
        if(arg.isEmpty()){send("+OK "+messages.size()+" messages");for(int i=0;i<messages.size();i++)if(!deleted.contains(i))out.print((i+1)+" "+messages.get(i).length()+"\r\n");out.print(".\r\n");out.flush();}
        else{try{int n=Integer.parseInt(arg)-1;if(n<0||n>=messages.size()||deleted.contains(n)){send("-ERR No such message");}else send("+OK "+(n+1)+" "+messages.get(n).length());}catch(NumberFormatException e){send("-ERR Invalid number");}}
    }
    private void handleRetr(String arg){
        if(!authenticated){send("-ERR Not authenticated");return;}
        try{int n=Integer.parseInt(arg)-1;if(n<0||n>=messages.size()||deleted.contains(n)){send("-ERR No such message");return;}
            File f=messages.get(n);send("+OK "+f.length()+" octets");
            BufferedReader r=new BufferedReader(new FileReader(f));String l;
            while((l=r.readLine())!=null){if(l.startsWith("."))out.print("."+l+"\r\n");else out.print(l+"\r\n");}
            r.close();out.print(".\r\n");out.flush();gui.onMessageRetrieved();
        }catch(Exception e){send("-ERR Error reading message");}
    }
    private void handleDele(String arg){
        if(!authenticated){send("-ERR Not authenticated");return;}
        try{int n=Integer.parseInt(arg)-1;if(n<0||n>=messages.size()||deleted.contains(n)){send("-ERR No such message");return;}
            deleted.add(n);send("+OK Message marked for deletion");gui.onMessageDeleted();
        }catch(NumberFormatException e){send("-ERR Invalid number");}
    }
    private void handleQuit(){
        for(int i:deleted){if(i<messages.size())messages.get(i).delete();}
        send("+OK POP3 server signing off");
    }
}
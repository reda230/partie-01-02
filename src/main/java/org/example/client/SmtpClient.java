package org.example.client;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.Properties;

/**
 * Partie 7 — Client SMTP JavaFX
 * Utilise Jakarta Mail pour envoyer des emails via le serveur SMTP (port 2525).
 */
public class SmtpClient extends Application {

    // ── Config serveur (à adapter si besoin) ──────────────────────────────
    private static final String SMTP_HOST = "localhost";
    private static final int    SMTP_PORT = 2525;

    // ── Champs UI ──────────────────────────────────────────────────────────
    private TextField    tfFrom, tfTo, tfSubject;
    private PasswordField pfPassword;
    private TextArea     taBody, taLog;
    private Button       btnSend;
    private Label        lblStatus;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Client SMTP — Messagerie");

        // ── En-tête ────────────────────────────────────────────────────────
        Label title = new Label("✉  Envoyer un Email");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#1e293b"));

        // ── Formulaire ─────────────────────────────────────────────────────
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(0, 0, 8, 0));

        tfFrom     = styledField("votre_username");
        pfPassword = new PasswordField();
        pfPassword.setPromptText("mot de passe");
        styleControl(pfPassword);

        tfTo      = styledField("destinataire");
        tfSubject = styledField("Objet du message");

        form.add(boldLabel("De (username) :"), 0, 0);
        form.add(tfFrom,     1, 0);
        form.add(boldLabel("Mot de passe :"),  0, 1);
        form.add(pfPassword, 1, 1);
        form.add(boldLabel("À :"),             0, 2);
        form.add(tfTo,       1, 2);
        form.add(boldLabel("Sujet :"),         0, 3);
        form.add(tfSubject,  1, 3);

        ColumnConstraints col0 = new ColumnConstraints(120);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(col0, col1);

        // ── Corps du message ───────────────────────────────────────────────
        Label lblBody = boldLabel("Message :");
        taBody = new TextArea();
        taBody.setPromptText("Écrivez votre message ici...");
        taBody.setPrefRowCount(6);
        taBody.setWrapText(true);
        taBody.setStyle(fieldStyle());

        // ── Bouton envoyer ─────────────────────────────────────────────────
        btnSend = new Button("Envoyer ▶");
        btnSend.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold;" +
                        "-fx-padding: 9 28; -fx-background-radius: 8;"
        );
        btnSend.setOnMouseEntered(e -> btnSend.setStyle(
                "-fx-background-color: #1d4ed8; -fx-text-fill: white;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold;" +
                        "-fx-padding: 9 28; -fx-background-radius: 8;"
        ));
        btnSend.setOnMouseExited(e -> btnSend.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold;" +
                        "-fx-padding: 9 28; -fx-background-radius: 8;"
        ));
        btnSend.setOnAction(e -> sendEmail());

        lblStatus = new Label();
        lblStatus.setFont(Font.font("Segoe UI", 13));

        HBox btnRow = new HBox(14, btnSend, lblStatus);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        // ── Log des échanges ───────────────────────────────────────────────
        Label lblLog = boldLabel("Journal des échanges :");
        taLog = new TextArea();
        taLog.setEditable(false);
        taLog.setPrefRowCount(7);
        taLog.setWrapText(true);
        taLog.setStyle(
                "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;" +
                        "-fx-background-color: #0f172a; -fx-text-fill: #86efac;" +
                        "-fx-background-radius: 8; -fx-border-radius: 8;" +
                        "-fx-border-color: #1e3a5f; -fx-border-width: 1;"
        );

        // ── Layout principal ───────────────────────────────────────────────
        VBox root = new VBox(14,
                title,
                separator(),
                form,
                lblBody, taBody,
                btnRow,
                separator(),
                lblLog, taLog
        );
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #f8fafc;");

        Scene scene = new Scene(root, 580, 640);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        log("▶ Client SMTP prêt — serveur : " + SMTP_HOST + ":" + SMTP_PORT);
    }

    // ── Envoi via Jakarta Mail ────────────────────────────────────────────
    private void sendEmail() {
        String from     = tfFrom.getText().trim();
        String password = pfPassword.getText();
        String to       = tfTo.getText().trim();
        String subject  = tfSubject.getText().trim();
        String body     = taBody.getText().trim();

        // Validation basique
        if (from.isEmpty() || to.isEmpty() || subject.isEmpty()) {
            setStatus("⚠ Remplissez De, À et Sujet.", false);
            return;
        }

        btnSend.setDisable(true);
        setStatus("Envoi en cours...", true);

        // Envoi dans un thread séparé pour ne pas bloquer l'UI
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
                props.put("mail.smtp.auth", "false");   // notre serveur n'exige pas AUTH SMTP
                props.put("mail.debug", "false");

                Session session = Session.getInstance(props);

                // Construction du message
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(from + "@example.com"));
                msg.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(to.contains("@") ? to : to + "@example.com"));
                msg.setSubject(subject);
                msg.setText(body);

                // Log avant envoi
                Platform.runLater(() -> {
                    log("C: EHLO client");
                    log("C: MAIL FROM:<" + from + "@example.com>");
                    log("C: RCPT TO:<" + (to.contains("@") ? to : to + "@example.com") + ">");
                    log("C: DATA");
                    log("C: Subject: " + subject);
                    log("C: [corps du message]");
                    log("C: .");
                });

                Transport.send(msg);

                Platform.runLater(() -> {
                    log("S: 250 OK — Message envoyé ✓");
                    setStatus("✅ Email envoyé avec succès !", true);
                    btnSend.setDisable(false);
                    taBody.clear();
                    tfSubject.clear();
                });

            } catch (AuthenticationFailedException ex) {
                Platform.runLater(() -> {
                    log("S: 535 Authentication failed");
                    setStatus("❌ Authentification échouée.", false);
                    btnSend.setDisable(false);
                });
            } catch (SendFailedException ex) {
                Platform.runLater(() -> {
                    log("S: 550 User not found");
                    setStatus("❌ Destinataire introuvable.", false);
                    btnSend.setDisable(false);
                });
            } catch (MessagingException ex) {
                Platform.runLater(() -> {
                    log("S: ERREUR — " + ex.getMessage());
                    setStatus("❌ Erreur : " + ex.getMessage(), false);
                    btnSend.setDisable(false);
                });
            }
        }).start();
    }

    // ── Helpers UI ────────────────────────────────────────────────────────
    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        styleControl(tf);
        return tf;
    }

    private void styleControl(Control c) {
        c.setStyle(fieldStyle());
        c.setMaxWidth(Double.MAX_VALUE);
    }

    private String fieldStyle() {
        return "-fx-background-color: white; -fx-border-color: #cbd5e1;" +
                "-fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-padding: 6 10; -fx-font-size: 13px;";
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        l.setTextFill(Color.web("#374151"));
        return l;
    }

    private Separator separator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #e2e8f0;");
        return sep;
    }

    private void log(String line) {
        taLog.appendText(line + "\n");
    }

    private void setStatus(String msg, boolean ok) {
        lblStatus.setText(msg);
        lblStatus.setTextFill(ok ? Color.web("#16a34a") : Color.web("#dc2626"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
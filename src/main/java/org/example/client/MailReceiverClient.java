package org.example.client;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
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

public class MailReceiverClient extends Application {

    private static final String HOST = "localhost";
    private static final String IMAP_PORT = "143";
    private static final String POP3_PORT = "110";

    private TextField tfUser;
    private PasswordField pfPassword;
    private ComboBox<String> cbProtocol;
    private TextArea taEmails, taLog;
    private Button btnFetch;
    private Label lblStatus;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Client IMAP / POP3");

        Label title = new Label("📥 Récupérer les Emails");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));

        tfUser = new TextField();
        tfUser.setPromptText("username");

        pfPassword = new PasswordField();
        pfPassword.setPromptText("mot de passe");

        cbProtocol = new ComboBox<>();
        cbProtocol.getItems().addAll("IMAP", "POP3");
        cbProtocol.setValue("IMAP");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);

        form.add(new Label("Utilisateur :"), 0, 0);
        form.add(tfUser, 1, 0);
        form.add(new Label("Mot de passe :"), 0, 1);
        form.add(pfPassword, 1, 1);
        form.add(new Label("Protocole :"), 0, 2);
        form.add(cbProtocol, 1, 2);

        taEmails = new TextArea();
        taEmails.setEditable(false);
        taEmails.setPrefHeight(200);

        taLog = new TextArea();
        taLog.setEditable(false);
        taLog.setPrefHeight(120);

        btnFetch = new Button("Récupérer ▶");
        lblStatus = new Label();

        btnFetch.setOnAction(e -> fetchEmails());

        VBox root = new VBox(10,
                title,
                form,
                btnFetch,
                lblStatus,
                new Label("Emails :"),
                taEmails,
                new Label("Log :"),
                taLog
        );

        root.setPadding(new Insets(15));

        stage.setScene(new Scene(root, 500, 600));
        stage.show();
    }

    private void fetchEmails() {
        String user = tfUser.getText();
        String pass = pfPassword.getText();
        String protocol = cbProtocol.getValue();

        btnFetch.setDisable(true);
        taEmails.clear();

        new Thread(() -> {
            try {
                Properties props = new Properties();

                String port = protocol.equals("IMAP") ? IMAP_PORT : POP3_PORT;
                String storeType = protocol.equals("IMAP") ? "imap" : "pop3";

                props.put("mail.store.protocol", storeType);
                props.put("mail.imap.host", HOST);
                props.put("mail.imap.port", "143");
                props.put("mail.imap.starttls.enable", "false"); // ou true si TLS
                props.put("mail." + storeType + ".host", HOST);
                props.put("mail." + storeType + ".port", port);

                Session session = Session.getInstance(props);
                Store store = session.getStore(storeType);

                log("Connexion à " + protocol + "...");
                store.connect(HOST, user, pass);

                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);

                Message[] messages = inbox.getMessages();

                for (Message msg : messages) {
                    String from = ((InternetAddress) msg.getFrom()[0]).getAddress();
                    String subject = msg.getSubject();


                    String line = "De: " + from + "\nSujet: " + subject + "\n " +  "\n";

                    Platform.runLater(() -> taEmails.appendText(line));
                }

                inbox.close(false);
                store.close();

                Platform.runLater(() -> {
                    setStatus("✅ Emails récupérés !");
                    btnFetch.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("❌ Erreur: " + e.getMessage());
                    btnFetch.setDisable(false);
                });
            }
        }).start();
    }

    private void log(String msg) {
        Platform.runLater(() -> taLog.appendText(msg + "\n"));
    }

    private void setStatus(String msg) {
        lblStatus.setText(msg);
        lblStatus.setTextFill(msg.startsWith("✅") ? Color.GREEN : Color.RED);
    }

    public static void main(String[] args) {
        launch();
    }
}
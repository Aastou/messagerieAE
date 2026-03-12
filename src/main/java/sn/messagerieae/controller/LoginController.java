package sn.messagerieae.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import sn.messagerieae.client.ClientSocket;
import sn.messagerieae.client.MessageListener;
import sn.messagerieae.client.SceneManager;
import sn.messagerieae.protocol.ProtocolMessage;
import sn.messagerieae.protocol.ProtocolMessage.Command;

public class LoginController implements MessageListener {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final ClientSocket client = ClientSocket.getInstance();
    private boolean loginPending = false;

    @FXML
    public void initialize() {
        client.addListener(this);
        client.setOnDisconnect(
                () -> showError("Connexion au serveur perdue"));
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }
        if (!client.isConnected()) {
            try {
                client.reconnect();
            } catch (Exception e) {
                showError("Impossible de se connecter au serveur");
                return;
            }
        }
        hideError();
        loginPending = true;
        client.sendLogin(username, password);
    }

    @FXML
    private void goToRegister() {
        client.removeListener(this);
        SceneManager.switchTo("register.fxml", "Messagerie — Inscription");
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case SUCCESS -> {
                if (!loginPending) return;
                loginPending = false;
                client.setCurrentUsername(message.getSender());
                client.setCurrentRole(message.getRole());
                client.removeListener(this);
                SceneManager.switchTo("chat.fxml",
                        "Messagerie — " + message.getSender());
            }
            case ERROR -> showError(message.getContent());
            default -> {}
        }
    }

    private void showError(String text) {
        errorLabel.setText(text);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}

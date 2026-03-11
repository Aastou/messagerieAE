package sn.messagerieae.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import sn.messagerieae.client.ClientSocket;
import sn.messagerieae.client.MessageListener;
import sn.messagerieae.client.SceneManager;
import sn.messagerieae.protocol.ProtocolMessage;

public class RegisterController implements MessageListener {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private final ClientSocket client = ClientSocket.getInstance();

    @FXML
    public void initialize() {
        client.addListener(this);
        roleComboBox.setItems(FXCollections.observableArrayList(
                "MEMBRE", "BENEVOLE", "ORGANISATEUR"));
        roleComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String role = roleComboBox.getValue();

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }
        if (username.length() < 3) {
            showError("Username : minimum 3 caractères");
            return;
        }
        if (password.length() < 4) {
            showError("Mot de passe : minimum 4 caractères");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Les mots de passe ne correspondent pas");
            return;
        }
        if (role == null) {
            showError("Veuillez sélectionner un rôle");
            return;
        }
        if (!client.isConnected()) {
            showError("Non connecté au serveur");
            return;
        }
        hideMessages();
        client.sendRegister(username, password, role);
    }

    @FXML
    private void goToLogin() {
        client.removeListener(this);
        SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case SUCCESS -> {
                showSuccess("Inscription réussie !");
                usernameField.clear();
                passwordField.clear();
                confirmPasswordField.clear();
            }
            case ERROR -> showError(message.getContent());
            default -> {}
        }
    }

    private void showError(String text) {
        successLabel.setVisible(false);
        successLabel.setManaged(false);
        errorLabel.setText(text);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showSuccess(String text) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setText(text);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }

    private void hideMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }
}

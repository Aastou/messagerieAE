package sn.messagerieae.controller;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import sn.messagerieae.client.ClientSocket;
import sn.messagerieae.client.MessageListener;
import sn.messagerieae.client.SceneManager;
import sn.messagerieae.protocol.ProtocolMessage;

/** Contrôleur de la vue d'inscription. Gère la validation du formulaire et l'envoi au serveur. */
public class RegisterController implements MessageListener {

    @FXML private TextField usernameField;        // Champ nom d'utilisateur
    @FXML private PasswordField passwordField;    // Champ mot de passe
    @FXML private PasswordField confirmPasswordField; // Confirmation du mot de passe
    @FXML private ComboBox<String> roleComboBox;  // Sélection du rôle (MEMBRE, BENEVOLE, ORGANISATEUR)
    @FXML private Label errorLabel;               // Message d'erreur (caché par défaut)
    @FXML private Label successLabel;             // Message de succès (caché par défaut)

    private final ClientSocket client = ClientSocket.getInstance(); // Singleton du socket client

    // Initialisation de la vue : enregistrement du listener et peuplement du ComboBox
    @FXML
    public void initialize() {
        client.addListener(this);
        roleComboBox.setItems(FXCollections.observableArrayList(
                "MEMBRE", "BENEVOLE", "ORGANISATEUR"));
        roleComboBox.getSelectionModel().selectFirst(); // MEMBRE sélectionné par défaut
    }

    // Gestionnaire du bouton "S'inscrire" : validation puis envoi au serveur
    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String role = roleComboBox.getValue();

        // Tous les champs sont obligatoires
        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }
        // Longueur minimale du nom d'utilisateur
        if (username.length() < 3) {
            showError("Username : minimum 3 caractères");
            return;
        }
        // Longueur minimale du mot de passe
        if (password.length() < 4) {
            showError("Mot de passe : minimum 4 caractères");
            return;
        }
        // Les deux mots de passe doivent correspondre
        if (!password.equals(confirm)) {
            showError("Les mots de passe ne correspondent pas");
            return;
        }
        // Un rôle doit être sélectionné
        if (role == null) {
            showError("Veuillez sélectionner un rôle");
            return;
        }
        // Connexion au serveur requise avant d'envoyer
        if (!client.isConnected()) {
            showError("Non connecté au serveur");
            return;
        }
        hideMessages();
        client.sendRegister(username, password, role);
    }

    // Lien "Déjà un compte ?" → retour vers la vue de connexion
    @FXML
    private void goToLogin() {
        client.removeListener(this);
        SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
    }

    // Traitement des réponses serveur (SUCCESS ou ERROR)
    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case SUCCESS -> {
                showSuccess("Inscription réussie ! Redirection...");
                // Vide le formulaire après l'inscription réussie
                usernameField.clear();
                passwordField.clear();
                confirmPasswordField.clear();
                // Redirection automatique vers le login après 1,5 secondes
                PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                pause.setOnFinished(e -> {
                    client.removeListener(this);
                    SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
                });
                pause.play();
            }
            case ERROR -> showError(message.getContent()); // Ex. : username déjà pris
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
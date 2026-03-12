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

/**
 * Contrôleur de la vue de connexion (login.fxml).
 *
 * <p>Gère la saisie des identifiants utilisateur, l'envoi de la requête
 * de connexion au serveur et la navigation vers la vue de chat en cas
 * de succès, ou l'affichage d'un message d'erreur en cas d'échec.</p>
 *
 * <p>Implémente {@link MessageListener} pour recevoir les réponses du
 * serveur de manière asynchrone.</p>
 */
public class LoginController implements MessageListener {

    /** Champ de saisie du nom d'utilisateur. */
    @FXML private TextField usernameField;

    /** Champ de saisie du mot de passe (masqué). */
    @FXML private PasswordField passwordField;

    /** Label d'affichage des messages d'erreur (caché par défaut). */
    @FXML private Label errorLabel;

    /** Instance unique du socket client (singleton). */
    private final ClientSocket client = ClientSocket.getInstance();

    /**
     * Indique qu'une tentative de connexion est en cours.
     * Évite de traiter des réponses SUCCESS non sollicitées.
     */
    private boolean loginPending = false;

    /**
     * Méthode d'initialisation appelée automatiquement par JavaFX
     * après le chargement du fichier FXML.
     *
     * <p>Enregistre ce contrôleur comme listener du client et configure
     * le callback de déconnexion.</p>
     */
    @FXML
    public void initialize() {
        client.addListener(this);
        // Affiche une erreur si la connexion au serveur est perdue
        client.setOnDisconnect(
                () -> showError("Connexion au serveur perdue"));
    }

    /**
     * Gestionnaire du bouton "Se connecter".
     *
     * <p>Valide les champs, tente une reconnexion si nécessaire,
     * puis envoie la requête LOGIN au serveur.</p>
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validation : les deux champs sont obligatoires
        if (username.isEmpty() || password.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }

        // Tentative de reconnexion si le socket est fermé
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

    /**
     * Gestionnaire du lien "Créer un compte".
     * Navigue vers la vue d'inscription.
     */
    @FXML
    private void goToRegister() {
        client.removeListener(this);
        SceneManager.switchTo("register.fxml", "Messagerie — Inscription");
    }

    /**
     * Reçoit et traite les messages envoyés par le serveur.
     *
     * <ul>
     *   <li>{@link Command#SUCCESS} : connexion acceptée → navigation vers le chat.</li>
     *   <li>{@link Command#ERROR}   : connexion refusée → affichage de l'erreur.</li>
     * </ul>
     *
     * @param message le message reçu du serveur
     */
    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case SUCCESS -> {
                // On ne traite le succès que si une tentative est en cours
                if (!loginPending) return;
                loginPending = false;
                // Stocke l'identité de l'utilisateur connecté dans le client
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

    /**
     * Affiche un message d'erreur sous le formulaire.
     *
     * @param text le texte d'erreur à afficher
     */
    private void showError(String text) {
        errorLabel.setText(text);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /** Cache le label d'erreur. */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
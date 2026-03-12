package sn.messagerieae.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import sn.messagerieae.client.ClientSocket;
import sn.messagerieae.client.MessageListener;
import sn.messagerieae.client.SceneManager;
import sn.messagerieae.protocol.ProtocolMessage;
import sn.messagerieae.protocol.ProtocolMessage.Command;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Contrôleur principal du chat. Gère la liste des utilisateurs, les messages et la déconnexion. */
public class ChatController implements MessageListener {

    @FXML private Label currentUserLabel;   // Affiche le nom de l'utilisateur connecté
    @FXML private Label roleLabel;          // Affiche le rôle (MEMBRE, BENEVOLE, ORGANISATEUR)
    @FXML private Label onlineCountLabel;   // Nombre d'utilisateurs en ligne
    @FXML private Label chattingWithLabel;  // Nom du correspondant actif
    @FXML private Label charCountLabel;     // Compteur de caractères (max 1000)
    @FXML private ListView<String> usersListView;   // Liste des utilisateurs connectés
    @FXML private VBox messagesContainer;           // Conteneur des bulles de messages
    @FXML private ScrollPane messagesScrollPane;    // Scroll de la zone de messages
    @FXML private TextField messageField;           // Champ de saisie du message
    @FXML private HBox conversationHeader;          // En-tête de la conversation (caché si aucun chat ouvert)
    @FXML private HBox inputArea;                   // Zone de saisie (cachée si aucun chat ouvert)
    @FXML private Button allMembersBtn;             // Bouton visible uniquement pour ORGANISATEUR
    @FXML private Button sendBtn;                   // Bouton Envoyer (désactivé si champ vide)

    private final ClientSocket client = ClientSocket.getInstance(); // Singleton du socket client
    private final Gson gson = new Gson();                           // Sérialiseur/désérialiseur JSON
    private String selectedUser = null;                             // Utilisateur sélectionné dans la liste
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm"); // Format d'affichage de l'heure

    // Initialisation de la vue chat : affichage de l'identité, règles métier et écoute des événements
    @FXML
    public void initialize() {
        client.addListener(this);
        currentUserLabel.setText(client.getCurrentUsername());
        roleLabel.setText(client.getCurrentRole());

        // RG13 : bouton "Tous les membres" visible uniquement pour les ORGANISATEURS
        if ("ORGANISATEUR".equals(client.getCurrentRole())) {
            allMembersBtn.setVisible(true);
            allMembersBtn.setManaged(true);
        }

        // RG7 : compteur de caractères en temps réel + activation du bouton Envoyer
        sendBtn.setDisable(true);
        messageField.textProperty().addListener((obs, o, n) -> {
            int len = n != null ? n.length() : 0;
            charCountLabel.setText(len + "/1000");
            charCountLabel.setStyle(len > 1000 ? "-fx-text-fill: red;" : ""); // Rouge si dépassement
            sendBtn.setDisable(n == null || n.trim().isEmpty()); // Désactivé si vide
        });

        // Ouverture d'une conversation lors du clic sur un utilisateur dans la liste
        usersListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> {
                    if (n != null) {
                        selectedUser = n;
                        openConversation(n);
                    }
                });

        // RG10 : affiche une alerte et retourne au login en cas de perte de connexion
        client.setOnDisconnect(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connexion perdue");
            alert.setHeaderText(null);
            alert.setContentText("La connexion au serveur a été perdue.");
            alert.showAndWait();
            client.removeListener(this);
            SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
        });

        // Charge immédiatement la liste des utilisateurs en ligne
        client.requestOnlineUsers();
    }

    // Ouvre la conversation avec l'utilisateur sélectionné et charge l'historique
    private void openConversation(String username) {
        chattingWithLabel.setText("Conversation avec " + username);
        conversationHeader.setVisible(true);
        conversationHeader.setManaged(true);
        inputArea.setVisible(true);
        inputArea.setManaged(true);
        messagesContainer.getChildren().clear(); // Efface les messages de la conversation précédente
        messageField.clear();
        sendBtn.setDisable(true);
        client.requestHistory(username); // Demande l'historique au serveur
    }

    // Envoi d'un message : validation, affichage local de la bulle et vidage du champ
    @FXML
    private void handleSendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty() || selectedUser == null) return; // Sécurité : ne devrait pas arriver
        if (content.length() > 1000) {
            showAlert("Max 1000 caractères");
            return;
        }
        client.sendMessage(selectedUser, content);
        // Affiche immédiatement la bulle côté émetteur sans attendre la confirmation serveur
        addMessageBubble(client.getCurrentUsername(), content,
                LocalDateTime.now().format(TIME_FORMAT), true);
        messageField.clear();
        scrollToBottom();
    }

    // Bouton de rafraîchissement : redemande la liste des utilisateurs en ligne
    @FXML
    private void handleRefreshUsers() {
        client.requestOnlineUsers();
    }

    // Bouton "Tous les membres" (ORGANISATEUR uniquement) : demande la liste complète
    @FXML
    private void handleGetAllMembers() {
        client.requestAllMembers();
    }

    // Déconnexion : nettoyage du client et retour à la vue de login
    @FXML
    private void handleLogout() {
        client.removeListener(this);
        client.setOnDisconnect(null); // Désactive le callback de déconnexion forcée
        client.sendLogout();
        client.setCurrentUsername(null);
        client.setCurrentRole(null);
        client.disconnect();
        SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
    }

    // Dispatch des messages reçus du serveur vers les handlers appropriés
    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case INCOMING_MESSAGE  -> handleIncomingMessage(message);
            case MESSAGE_HISTORY   -> handleMessageHistory(message);
            case USER_LIST         -> handleUserList(message);
            case USER_CONNECTED    -> handleUserConnected(message);
            case USER_DISCONNECTED -> handleUserDisconnected(message);
            case ERROR             -> showAlert(message.getContent());
            default -> {}
        }
    }

    // Message reçu d'un autre utilisateur : affiché si la conv est ouverte, sinon notification
    private void handleIncomingMessage(ProtocolMessage msg) {
        String sender  = msg.getSender();
        String content = msg.getContent();
        String time    = formatTime(msg.getExtra());
        if (sender.equals(selectedUser)) {
            // La conversation avec cet utilisateur est active → affiche la bulle
            addMessageBubble(sender, content, time, false);
            scrollToBottom();
        } else {
            // Conversation non active → notification temporaire en haut de l'écran
            showNotification(sender, content);
        }
    }

    // Réception de l'historique : reconstruit toutes les bulles de la conversation
    private void handleMessageHistory(ProtocolMessage msg) {
        messagesContainer.getChildren().clear();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> messages = gson.fromJson(msg.getExtra(), listType);
        if (messages == null || messages.isEmpty()) {
            // Aucun échange précédent avec cet utilisateur
            Label empty = new Label("Aucun message.");
            empty.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            messagesContainer.getChildren().add(empty);
            return;
        }
        for (Map<String, Object> m : messages) {
            String sender  = (String) m.get("sender");
            String contenu = (String) m.get("contenu");
            String date    = (String) m.get("dateEnvoi");
            boolean isMe   = sender.equals(client.getCurrentUsername()); // Alignement droite/gauche
            addMessageBubble(sender, contenu, formatTime(date), isMe);
        }
        scrollToBottom();
    }

    // Mise à jour de la liste des utilisateurs (en ligne ou tous les membres)
    private void handleUserList(ProtocolMessage msg) {
        if ("ALL_MEMBERS".equals(msg.getContent())) {
            // Cas particulier : affichage de tous les membres dans un tableau
            handleAllMembersList(msg);
            return;
        }
        Type listType = new TypeToken<List<String>>() {}.getType();
        List<String> users = gson.fromJson(msg.getExtra(), listType);
        usersListView.getItems().clear();
        if (users != null) usersListView.getItems().addAll(users);
        onlineCountLabel.setText("(" + usersListView.getItems().size() + ")");
    }

    private void handleAllMembersList(ProtocolMessage msg) {
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> members = gson.fromJson(msg.getExtra(), listType);
        if (members == null) members = List.of();

        // Colonne Nom
        TableColumn<Map<String, Object>, String> colNom = new TableColumn<>("Nom");
        colNom.setCellValueFactory(p ->
                new SimpleStringProperty(String.valueOf(p.getValue().get("username"))));

        // Colonne Rôle
        TableColumn<Map<String, Object>, String> colRole = new TableColumn<>("Rôle");
        colRole.setCellValueFactory(p ->
                new SimpleStringProperty(String.valueOf(p.getValue().get("role"))));

        // Colonne Statut avec pastille colorée
        TableColumn<Map<String, Object>, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(p ->
                new SimpleStringProperty(String.valueOf(p.getValue().get("status"))));
        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                boolean online = "ONLINE".equalsIgnoreCase(status);
                Circle dot = new Circle(5);
                dot.setFill(online ? Color.web("#4caf50") : Color.web("#9e9e9e"));
                Label lbl = new Label(online ? "En ligne" : "Hors ligne");
                lbl.setStyle("-fx-font-size: 12px;");
                HBox cell = new HBox(6, dot, lbl);
                cell.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cell);
                setText(null);
            }
        });

        TableView<Map<String, Object>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);
        table.getColumns().addAll(colNom, colRole, colStatut);
        table.setItems(FXCollections.observableArrayList(members));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Liste des membres");
        dialog.setHeaderText("Total : " + members.size() + " membre(s)");
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(table);
        pane.setPrefWidth(450);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        String css = getClass().getResource("/sn/messagerieae/styles/style.css").toExternalForm();
        if (css != null) pane.getStylesheets().add(css);

        dialog.show();
    }

    // Ajoute l'utilisateur qui vient de se connecter dans la liste (si absent)
    private void handleUserConnected(ProtocolMessage msg) {
        String user = msg.getSender();
        if (!usersListView.getItems().contains(user))
            usersListView.getItems().add(user);
        onlineCountLabel.setText("(" + usersListView.getItems().size() + ")");
    }

    // Retire l'utilisateur déconnecté de la liste
    private void handleUserDisconnected(ProtocolMessage msg) {
        usersListView.getItems().remove(msg.getSender());
        onlineCountLabel.setText("(" + usersListView.getItems().size() + ")");
    }

    /**
     * Crée et ajoute une bulle de message dans le conteneur.
     * Les bulles de l'utilisateur courant (isMe=true) sont alignées à droite,
     * celles des autres à gauche.
     */
    private void addMessageBubble(String sender, String content,
                                  String time, boolean isMe) {
        VBox bubble = new VBox(2);
        // Style CSS différent selon l'émetteur (bubble-me ou bubble-other)
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");
        bubble.setMaxWidth(400);
        if (!isMe) {
            // Affiche le nom de l'expéditeur uniquement pour les messages reçus
            Label nameLabel = new Label(sender);
            nameLabel.getStyleClass().add("bubble-sender");
            bubble.getChildren().add(nameLabel);
        }
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true); // Retour à la ligne automatique
        contentLabel.getStyleClass().add("bubble-content");
        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("bubble-time");
        bubble.getChildren().addAll(contentLabel, timeLabel);
        HBox row = new HBox();
        row.getChildren().add(bubble);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messagesContainer.getChildren().add(row);
    }

    // Notification temporaire (5 s) pour un message reçu hors conversation active
    private void showNotification(String sender, String content) {
        // Tronque le contenu à 50 caractères pour l'aperçu
        String preview = content.length() > 50
                ? content.substring(0, 50) + "..." : content;
        Label notif = new Label("💬 " + sender + " : " + preview);
        notif.getStyleClass().add("notification");
        // Cliquer sur la notification ouvre la conversation avec l'expéditeur
        notif.setOnMouseClicked(e ->
                usersListView.getSelectionModel().select(sender));
        messagesContainer.getChildren().add(0, notif); // Insère en haut de la liste
        // Supprime la notification après 5 secondes sur le thread JavaFX
        new Thread(() -> {
            try { Thread.sleep(5000); }
            catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(() ->
                    messagesContainer.getChildren().remove(notif));
        }).start();
    }

    // Force le scroll vers le bas à chaque changement de hauteur du conteneur
    private void scrollToBottom() {
        messagesContainer.heightProperty()
                .addListener((obs, o, n) ->
                        messagesScrollPane.setVvalue(1.0));
    }

    // Convertit une chaîne ISO datetime en format HH:mm ; retourne la chaîne brute en cas d'erreur
    private String formatTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr).format(TIME_FORMAT);
        } catch (Exception e) {
            return dateTimeStr != null ? dateTimeStr : "";
        }
    }

    private void showAlert(String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention");
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

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

public class ChatController implements MessageListener {

    @FXML private Label currentUserLabel;
    @FXML private Label roleLabel;
    @FXML private Label onlineCountLabel;
    @FXML private Label chattingWithLabel;
    @FXML private Label charCountLabel;
    @FXML private ListView<String> usersListView;
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private TextField messageField;
    @FXML private HBox conversationHeader;
    @FXML private HBox inputArea;
    @FXML private Button allMembersBtn;
    @FXML private Button sendBtn;

    private final ClientSocket client = ClientSocket.getInstance();
    private final Gson gson = new Gson();
    private String selectedUser = null;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        client.addListener(this);
        currentUserLabel.setText(client.getCurrentUsername());
        roleLabel.setText(client.getCurrentRole());

        // RG13 : bouton visible pour ORGANISATEUR
        if ("ORGANISATEUR".equals(client.getCurrentRole())) {
            allMembersBtn.setVisible(true);
            allMembersBtn.setManaged(true);
        }

        // RG7 : compteur de caractères + état bouton Envoyer
        sendBtn.setDisable(true);
        messageField.textProperty().addListener((obs, o, n) -> {
            int len = n != null ? n.length() : 0;
            charCountLabel.setText(len + "/1000");
            charCountLabel.setStyle(len > 1000 ? "-fx-text-fill: red;" : "");
            sendBtn.setDisable(n == null || n.trim().isEmpty());
        });

        // Sélection utilisateur
        usersListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> {
                    if (n != null) {
                        selectedUser = n;
                        openConversation(n);
                    }
                });

        // RG10 : perte de connexion
        client.setOnDisconnect(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connexion perdue");
            alert.setHeaderText(null);
            alert.setContentText("La connexion au serveur a été perdue.");
            alert.showAndWait();
            client.removeListener(this);
            SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
        });

        client.requestOnlineUsers();
    }

    private void openConversation(String username) {
        chattingWithLabel.setText("Conversation avec " + username);
        conversationHeader.setVisible(true);
        conversationHeader.setManaged(true);
        inputArea.setVisible(true);
        inputArea.setManaged(true);
        messagesContainer.getChildren().clear();
        messageField.clear();
        sendBtn.setDisable(true);
        client.requestHistory(username);
    }

    @FXML
    private void handleSendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty() || selectedUser == null) return;
        if (content.length() > 1000) {
            showAlert("Max 1000 caractères");
            return;
        }
        client.sendMessage(selectedUser, content);
        addMessageBubble(client.getCurrentUsername(), content,
                LocalDateTime.now().format(TIME_FORMAT), true);
        messageField.clear();
        scrollToBottom();
    }

    @FXML
    private void handleRefreshUsers() {
        client.requestOnlineUsers();
    }

    @FXML
    private void handleGetAllMembers() {
        client.requestAllMembers();
    }

    @FXML
    private void handleLogout() {
        client.removeListener(this);
        client.setOnDisconnect(null);
        client.sendLogout();
        client.setCurrentUsername(null);
        client.setCurrentRole(null);
        client.disconnect();
        SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        switch (message.getCommand()) {
            case INCOMING_MESSAGE -> handleIncomingMessage(message);
            case MESSAGE_HISTORY  -> handleMessageHistory(message);
            case USER_LIST        -> handleUserList(message);
            case USER_CONNECTED   -> handleUserConnected(message);
            case USER_DISCONNECTED -> handleUserDisconnected(message);
            case ERROR -> showAlert(message.getContent());
            default -> {}
        }
    }

    private void handleIncomingMessage(ProtocolMessage msg) {
        String sender  = msg.getSender();
        String content = msg.getContent();
        String time    = formatTime(msg.getExtra());
        if (sender.equals(selectedUser)) {
            addMessageBubble(sender, content, time, false);
            scrollToBottom();
        } else {
            showNotification(sender, content);
        }
    }

    private void handleMessageHistory(ProtocolMessage msg) {
        messagesContainer.getChildren().clear();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> messages = gson.fromJson(msg.getExtra(), listType);
        if (messages == null || messages.isEmpty()) {
            Label empty = new Label("Aucun message.");
            empty.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            messagesContainer.getChildren().add(empty);
            return;
        }
        for (Map<String, Object> m : messages) {
            String sender  = (String) m.get("sender");
            String contenu = (String) m.get("contenu");
            String date    = (String) m.get("dateEnvoi");
            boolean isMe   = sender.equals(client.getCurrentUsername());
            addMessageBubble(sender, contenu, formatTime(date), isMe);
        }
        scrollToBottom();
    }

    private void handleUserList(ProtocolMessage msg) {
        if ("ALL_MEMBERS".equals(msg.getContent())) {
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

    private void handleUserConnected(ProtocolMessage msg) {
        String user = msg.getSender();
        if (!usersListView.getItems().contains(user))
            usersListView.getItems().add(user);
        onlineCountLabel.setText("(" + usersListView.getItems().size() + ")");
    }

    private void handleUserDisconnected(ProtocolMessage msg) {
        usersListView.getItems().remove(msg.getSender());
        onlineCountLabel.setText("(" + usersListView.getItems().size() + ")");
    }

    private void addMessageBubble(String sender, String content,
                                  String time, boolean isMe) {
        VBox bubble = new VBox(2);
        bubble.getStyleClass().add(isMe ? "bubble-me" : "bubble-other");
        bubble.setMaxWidth(400);
        if (!isMe) {
            Label nameLabel = new Label(sender);
            nameLabel.getStyleClass().add("bubble-sender");
            bubble.getChildren().add(nameLabel);
        }
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("bubble-content");
        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("bubble-time");
        bubble.getChildren().addAll(contentLabel, timeLabel);
        HBox row = new HBox();
        row.getChildren().add(bubble);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messagesContainer.getChildren().add(row);
    }

    private void showNotification(String sender, String content) {
        String preview = content.length() > 50
                ? content.substring(0, 50) + "..." : content;
        Label notif = new Label("💬 " + sender + " : " + preview);
        notif.getStyleClass().add("notification");
        notif.setOnMouseClicked(e ->
                usersListView.getSelectionModel().select(sender));
        messagesContainer.getChildren().add(0,notif);
        new Thread(() -> {
            try { Thread.sleep(5000); }
            catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(() ->
                    messagesContainer.getChildren().remove(notif));
        }).start();
    }

    private void scrollToBottom() {
        messagesContainer.heightProperty()
                .addListener((obs, o, n) ->
                        messagesScrollPane.setVvalue(1.0));
    }

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

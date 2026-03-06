package sn.messagerieae.server;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.model.Message;
import sn.messagerieae.model.User;
import sn.messagerieae.model.enums.Role;
import sn.messagerieae.protocol.ProtocolMessage;
import sn.messagerieae.protocol.ProtocolMessage.Command;
import sn.messagerieae.service.AuthService;
import sn.messagerieae.service.MessageService;
import sn.messagerieae.service.UserService;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String username;
    private User currentUser;

    // Référence vers la map globale des clients connectés
    private final Map<String, ClientHandler> connectedClients;

    // Services
    private final AuthService authService;
    private final MessageService messageService;
    private final UserService userService;

    public ClientHandler(Socket socket,
                         Map<String, ClientHandler> connectedClients,
                         AuthService authService,
                         MessageService messageService,
                         UserService userService) {
        this.socket = socket;
        this.connectedClients = connectedClients;
        this.authService = authService;
        this.messageService = messageService;
        this.userService = userService;
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            String line;
            while ((line = input.readLine()) != null) {
                try {
                    ProtocolMessage request = ProtocolMessage.fromJson(line);
                    handleRequest(request);
                } catch (Exception e) {
                    sendResponse(ProtocolMessage.error("Requête invalide : " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            logger.warn("Perte de connexion pour : {}", username != null ? username : "inconnu");
        } finally {
            disconnect();
        }
    }

    // ===== Dispatcher des commandes =====
    private void handleRequest(ProtocolMessage request) {
        switch (request.getCommand()) {
            case REGISTER       -> handleRegister(request);
            case LOGIN          -> handleLogin(request);
            case SEND_MESSAGE   -> handleSendMessage(request);
            case GET_HISTORY    -> handleGetHistory(request);
            case GET_ONLINE_USERS -> handleGetOnlineUsers();
            case GET_ALL_MEMBERS  -> handleGetAllMembers();
            case LOGOUT         -> handleLogout();
            default             -> sendResponse(ProtocolMessage.error("Commande inconnue"));
        }
    }

    // ===== REGISTER =====
    private void handleRegister(ProtocolMessage request) {
        try {
            String reqUsername = request.getSender();
            String reqPassword = request.getContent();
            String reqRole = request.getRole();

            Role role = Role.valueOf(reqRole.toUpperCase());
            authService.register(reqUsername, reqPassword, role);

            sendResponse(ProtocolMessage.success("Inscription réussie pour : " + reqUsername));
            logger.info("Inscription : {}", reqUsername);

        } catch (IllegalArgumentException e) {
            sendResponse(ProtocolMessage.error(e.getMessage()));
        }
    }

    // ===== LOGIN =====
    private void handleLogin(ProtocolMessage request) {
        try {
            String reqUsername = request.getSender();
            String reqPassword = request.getContent();

            User user = authService.login(reqUsername, reqPassword);

            this.username = reqUsername;
            this.currentUser = user;
            connectedClients.put(reqUsername, this);

            // Réponse de succès avec le rôle
            ProtocolMessage response = ProtocolMessage.success("Connexion réussie");
            response.setRole(user.getRole().name());
            response.setSender(reqUsername);
            sendResponse(response);

            // RG6 : livrer les messages en attente
            List<Message> pending = messageService.deliverPendingMessages(user.getId());
            for (Message msg : pending) {
                ProtocolMessage incoming = new ProtocolMessage();
                incoming.setCommand(Command.INCOMING_MESSAGE);
                incoming.setSender(msg.getSender().getUsername());
                incoming.setContent(msg.getContenu());
                incoming.setExtra(msg.getDateEnvoi().toString());
                sendResponse(incoming);
            }

            // Notifier les autres utilisateurs de la connexion
            broadcastUserStatus(reqUsername, Command.USER_CONNECTED);
            logger.info("Connexion : {}", reqUsername);

        } catch (Exception e) {
            sendResponse(ProtocolMessage.error(e.getMessage()));
        }
    }

    // ===== SEND_MESSAGE (RG5 + RG6 + RG7) =====
    private void handleSendMessage(ProtocolMessage request) {
        try {
            if (currentUser == null) {
                sendResponse(ProtocolMessage.error("Vous devez être connecté (RG2)"));
                return;
            }

            String receiverUsername = request.getReceiver();
            String contenu = request.getContent();

            // Sauvegarder en base via le service (validations RG5 + RG7)
            Message saved = messageService.sendMessage(username, receiverUsername, contenu);

            // Confirmer l'envoi à l'expéditeur
            ProtocolMessage ack = ProtocolMessage.success("Message envoyé");
            ack.setExtra(saved.getId().toString());
            sendResponse(ack);

            // Si le destinataire est connecté → envoi en temps réel
            ClientHandler receiverHandler = connectedClients.get(receiverUsername);
            if (receiverHandler != null) {
                ProtocolMessage incoming = new ProtocolMessage();
                incoming.setCommand(Command.INCOMING_MESSAGE);
                incoming.setSender(username);
                incoming.setContent(contenu);
                incoming.setExtra(saved.getDateEnvoi().toString());
                receiverHandler.sendResponse(incoming);

                // Marquer comme reçu
                messageService.markAsReceived(saved.getId());
            }
            // Sinon le message reste en statut ENVOYE (RG6)

            logger.info("Message : {} → {}", username, receiverUsername);

        } catch (Exception e) {
            sendResponse(ProtocolMessage.error(e.getMessage()));
        }
    }

    // ===== GET_HISTORY (RG8) =====
    private void handleGetHistory(ProtocolMessage request) {
        try {
            if (currentUser == null) {
                sendResponse(ProtocolMessage.error("Vous devez être connecté (RG2)"));
                return;
            }

            String otherUsername = request.getReceiver();
            User otherUser = userService.findByUsername(otherUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + otherUsername));

            List<Message> history = messageService.getConversation(currentUser.getId(), otherUser.getId());

            // Marquer la conversation comme lue
            messageService.markConversationAsRead(otherUser.getId(), currentUser.getId());

            // Construire la réponse avec la liste des messages
            List<MessageDTO> dtos = history.stream()
                .map(m -> new MessageDTO(
                    m.getId(),
                    m.getSender().getUsername(),
                    m.getReceiver().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi().toString(),
                    m.getStatut().name()))
                .toList();

            Gson gson = new ProtocolMessage().getGson();
            ProtocolMessage response = new ProtocolMessage();
            response.setCommand(Command.MESSAGE_HISTORY);
            response.setExtra(gson.toJson(dtos));
            sendResponse(response);

        } catch (Exception e) {
            sendResponse(ProtocolMessage.error(e.getMessage()));
        }
    }

    // ===== GET_ONLINE_USERS =====
    private void handleGetOnlineUsers() {
        if (currentUser == null) {
            sendResponse(ProtocolMessage.error("Vous devez être connecté (RG2)"));
            return;
        }

        List<String> onlineUsernames = userService.getOnlineUsers().stream()
            .map(User::getUsername)
            .filter(u -> !u.equals(username)) // exclure soi-même
            .toList();

        Gson gson = new ProtocolMessage().getGson();
        ProtocolMessage response = new ProtocolMessage();
        response.setCommand(Command.USER_LIST);
        response.setExtra(gson.toJson(onlineUsernames));
        sendResponse(response);
    }

    // ===== GET_ALL_MEMBERS (RG13) =====
    private void handleGetAllMembers() {
        try {
            if (currentUser == null) {
                sendResponse(ProtocolMessage.error("Vous devez être connecté (RG2)"));
                return;
            }

            List<User> allMembers = userService.getAllMembers(currentUser);

            List<MemberDTO> dtos = allMembers.stream()
                .map(u -> new MemberDTO(
                    u.getId(),
                    u.getUsername(),
                    u.getRole().name(),
                    u.getStatus().name(),
                    u.getDateCreation().toString()))
                .toList();

            Gson gson = new ProtocolMessage().getGson();
            ProtocolMessage response = new ProtocolMessage();
            response.setCommand(Command.USER_LIST);
            response.setContent("ALL_MEMBERS");
            response.setExtra(gson.toJson(dtos));
            sendResponse(response);

        } catch (SecurityException e) {
            sendResponse(ProtocolMessage.error(e.getMessage()));
        }
    }

    // ===== LOGOUT =====
    private void handleLogout() {
        sendResponse(ProtocolMessage.success("Déconnexion réussie"));
        disconnect();
    }

    // ===== Déconnexion / nettoyage =====
    private void disconnect() {
        if (username != null) {
            authService.logout(username);
            connectedClients.remove(username);
            broadcastUserStatus(username, Command.USER_DISCONNECTED);
            logger.info("Déconnexion : {}", username);
        }
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Erreur fermeture socket", e);
        }
    }

    // ===== Envoyer une réponse au client =====
    public void sendResponse(ProtocolMessage message) {
        if (output != null) {
            output.println(message.toJson());
        }
    }

    // ===== Notifier tous les clients d'un changement de statut =====
    private void broadcastUserStatus(String user, Command statusCommand) {
        ProtocolMessage notification = new ProtocolMessage(statusCommand, user);
        notification.setSender(user);

        connectedClients.values().stream()
            .filter(client -> !client.getUsername().equals(user))
            .forEach(client -> client.sendResponse(notification));
    }

    public String getUsername() {
        return username;
    }

    // ===== DTOs internes pour la sérialisation =====
    private record MessageDTO(Long id, String sender, String receiver,
                              String contenu, String dateEnvoi, String statut) {}

    private record MemberDTO(Long id, String username, String role,
                             String status, String dateCreation) {}
}

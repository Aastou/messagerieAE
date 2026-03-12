package sn.messagerieae.client;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.protocol.ProtocolMessage;
import sn.messagerieae.protocol.ProtocolMessage.Command;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client TCP singleton. Gère la connexion au serveur, l'envoi/réception des messages et les listeners. */
public class ClientSocket {

    private static final Logger logger = LoggerFactory.getLogger(ClientSocket.class);

    private Socket socket;                // Socket TCP vers le serveur
    private BufferedReader input;         // Flux de lecture (réponses serveur)
    private PrintWriter output;           // Flux d'écriture (requêtes client)
    private Thread listenerThread;        // Thread dédié à l'écoute des messages entrants
    private volatile boolean connected = false; // État de connexion (volatile pour la visibilité inter-threads)
    private String currentUsername;       // Nom de l'utilisateur connecté
    private String currentRole;           // Rôle de l'utilisateur (MEMBRE, BENEVOLE, ORGANISATEUR)
    private String lastHost;              // Dernier hôte utilisé (pour reconnexion)
    private int lastPort;                 // Dernier port utilisé (pour reconnexion)
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>(); // Thread-safe
    private Runnable onDisconnect;        // Callback déclenché en cas de perte de connexion

    // ===== Singleton =====
    private static ClientSocket instance;

    private ClientSocket() {} // Constructeur privé : passe par getInstance()

    /** Retourne l'instance unique du client (création paresseuse). */
    public static ClientSocket getInstance() {
        if (instance == null) {
            instance = new ClientSocket();
        }
        return instance;
    }

    // ===== Connexion au serveur =====

    /** Ouvre une connexion TCP et démarre le thread d'écoute des messages. */
    public void connect(String host, int port) throws IOException {
        this.lastHost = host; // Sauvegarde pour une éventuelle reconnexion
        this.lastPort = port;
        socket = new Socket(host, port);
        input = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        output = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        connected = true;
        // Thread daemon : s'arrête automatiquement à la fermeture de l'application
        listenerThread = new Thread(this::listenForMessages, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        logger.info("Connecté au serveur {}:{}", host, port);
    }

    // ===== Écoute des messages serveur =====

    /** Boucle d'écoute bloquante exécutée dans un thread séparé. */
    private void listenForMessages() {
        try {
            String line;
            while (connected && (line = input.readLine()) != null) {
                ProtocolMessage message = ProtocolMessage.fromJson(line);
                logger.debug("Reçu : {}", message.getCommand());
                // Dispatch sur le thread JavaFX pour que les contrôleurs puissent modifier l'UI
                Platform.runLater(() -> {
                    for (MessageListener listener : listeners) {
                        listener.onMessage(message);
                    }
                });
            }
        } catch (IOException e) {
            if (connected) {
                // Perte de connexion inattendue : notifie l'UI via le callback
                logger.warn("Perte de connexion au serveur");
                connected = false;
                if (onDisconnect != null) {
                    Platform.runLater(onDisconnect);
                }
            }
        }
    }

    // ===== Envoyer une requête =====

    /** Sérialise et envoie un message au serveur (no-op si déconnecté). */
    public void send(ProtocolMessage message) {
        if (output != null && connected) {
            output.println(message.toJson());
            logger.debug("Envoyé : {}", message.getCommand());
        }
    }

    // ===== Requêtes pratiques (raccourcis pour construire et envoyer les ProtocolMessage) =====

    /** Envoie une requête de connexion avec les identifiants. */
    public void sendLogin(String username, String password) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.LOGIN);
        msg.setSender(username);
        msg.setContent(password);
        send(msg);
    }

    /** Envoie une requête d'inscription avec le rôle choisi. */
    public void sendRegister(String username, String password, String role) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.REGISTER);
        msg.setSender(username);
        msg.setContent(password);
        msg.setRole(role);
        send(msg);
    }

    /** Envoie un message privé à un destinataire. */
    public void sendMessage(String receiver, String content) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.SEND_MESSAGE);
        msg.setReceiver(receiver);
        msg.setContent(content);
        send(msg);
    }

    /** Demande l'historique des messages échangés avec un utilisateur. */
    public void requestHistory(String otherUsername) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_HISTORY);
        msg.setReceiver(otherUsername);
        send(msg);
    }

    /** Demande la liste des utilisateurs actuellement connectés. */
    public void requestOnlineUsers() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_ONLINE_USERS);
        send(msg);
    }

    /** Demande la liste complète de tous les membres inscrits (ORGANISATEUR uniquement). */
    public void requestAllMembers() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_ALL_MEMBERS);
        send(msg);
    }

    /** Notifie le serveur de la déconnexion volontaire de l'utilisateur. */
    public void sendLogout() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.LOGOUT);
        send(msg);
    }

    /** Reconnecte au dernier serveur connu (utilisé si la connexion a été perdue). */
    public void reconnect() throws IOException {
        if (lastHost != null) {
            connect(lastHost, lastPort);
        }
    }

    // ===== Déconnexion =====
    public void disconnect() {
        connected = false;
        currentUsername = null;
        currentRole = null;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("Erreur lors de la déconnexion", e);
        }
        logger.info("Déconnecté du serveur");
    }

    // ===== Gestion des listeners =====
    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() { listeners.clear(); }

    // ===== Getters / Setters =====
    public boolean isConnected() { return connected; }
    public String getCurrentUsername() { return currentUsername; }
    public void setCurrentUsername(String u) { this.currentUsername = u; }
    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String r) { this.currentRole = r; }
    public void setOnDisconnect(Runnable cb) { this.onDisconnect = cb; }
}

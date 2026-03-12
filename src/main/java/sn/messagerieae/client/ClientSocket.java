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

public class ClientSocket {

    private static final Logger logger = LoggerFactory.getLogger(ClientSocket.class);

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private Thread listenerThread;
    private volatile boolean connected = false;
    private String currentUsername;
    private String currentRole;
    private String lastHost;
    private int lastPort;
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private Runnable onDisconnect;

    // ===== Singleton =====
    private static ClientSocket instance;

    private ClientSocket() {}

    public static ClientSocket getInstance() {
        if (instance == null) {
            instance = new ClientSocket();
        }
        return instance;
    }

    // ===== Connexion au serveur =====
    public void connect(String host, int port) throws IOException {
        this.lastHost = host;
        this.lastPort = port;
        socket = new Socket(host, port);
        input = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        output = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        connected = true;
        listenerThread = new Thread(this::listenForMessages, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        logger.info("Connecté au serveur {}:{}", host, port);
    }

    // ===== Écoute des messages serveur =====
    private void listenForMessages() {
        try {
            String line;
            while (connected && (line = input.readLine()) != null) {
                ProtocolMessage message = ProtocolMessage.fromJson(line);
                logger.debug("Reçu : {}", message.getCommand());
                Platform.runLater(() -> {
                    for (MessageListener listener : listeners) {
                        listener.onMessage(message);
                    }
                });
            }
        } catch (IOException e) {
            if (connected) {
                logger.warn("Perte de connexion au serveur");
                connected = false;
                if (onDisconnect != null) {
                    Platform.runLater(onDisconnect);
                }
            }
        }
    }

    // ===== Envoyer une requête =====
    public void send(ProtocolMessage message) {
        if (output != null && connected) {
            output.println(message.toJson());
            logger.debug("Envoyé : {}", message.getCommand());
        }
    }

    // ===== Requêtes pratiques =====
    public void sendLogin(String username, String password) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.LOGIN);
        msg.setSender(username);
        msg.setContent(password);
        send(msg);
    }

    public void sendRegister(String username, String password, String role) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.REGISTER);
        msg.setSender(username);
        msg.setContent(password);
        msg.setRole(role);
        send(msg);
    }

    public void sendMessage(String receiver, String content) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.SEND_MESSAGE);
        msg.setReceiver(receiver);
        msg.setContent(content);
        send(msg);
    }

    public void requestHistory(String otherUsername) {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_HISTORY);
        msg.setReceiver(otherUsername);
        send(msg);
    }

    public void requestOnlineUsers() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_ONLINE_USERS);
        send(msg);
    }

    public void requestAllMembers() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.GET_ALL_MEMBERS);
        send(msg);
    }

    public void sendLogout() {
        ProtocolMessage msg = new ProtocolMessage();
        msg.setCommand(Command.LOGOUT);
        send(msg);
    }

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

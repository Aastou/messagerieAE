package sn.messagerieae.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.dao.MessageDAO;
import sn.messagerieae.dao.UserDAO;
import sn.messagerieae.service.AuthService;
import sn.messagerieae.service.MessageService;
import sn.messagerieae.service.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int DEFAULT_PORT = 5555;

    private final int port;
    private ServerSocket serverSocket;
    private boolean running;

    // Map thread-safe des clients connectés (RG3 + RG11)
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    // DAOs
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    // Services
    private final AuthService authService = new AuthService(userDAO);
    private final UserService userService = new UserService(userDAO);
    private final MessageService messageService = new MessageService(messageDAO, userDAO, authService);

    public Server() {
        this(DEFAULT_PORT);
    }

    public Server(int port) {
        this.port = port;
    }

    // ===== Démarrer le serveur =====
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("========================================");
            logger.info("  Serveur démarré sur le port {}", port);
            logger.info("  En attente de connexions...");
            logger.info("========================================");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Nouvelle connexion depuis : {}", clientSocket.getRemoteSocketAddress());

                // RG11 : chaque client dans un thread séparé
                ClientHandler handler = new ClientHandler(
                    clientSocket,
                    connectedClients,
                    authService,
                    messageService,
                    userService
                );
                new Thread(handler).start();
            }

        } catch (IOException e) {
            if (running) {
                logger.error("Erreur serveur : {}", e.getMessage());
            }
        }
    }

    // ===== Arrêter le serveur =====
    public void stop() {
        running = false;
        // Déconnecter tous les clients
        connectedClients.values().forEach(client ->
            client.sendResponse(
                new sn.messagerieae.protocol.ProtocolMessage(
                    sn.messagerieae.protocol.ProtocolMessage.Command.ERROR,
                    "Le serveur s'arrête"
                )
            )
        );
        connectedClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Erreur lors de l'arrêt du serveur", e);
        }
        logger.info("Serveur arrêté");
    }

    public int getPort() {
        return port;
    }

    public int getConnectedClientsCount() {
        return connectedClients.size();
    }

    // ===== Point d'entrée =====
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Server server = new Server(port);

        // Hook pour arrêt propre (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Arrêt en cours...");
            server.stop();
            sn.messagerieae.util.JpaUtil.close();
        }));

        server.start();
    }
}

package sn.messagerieae.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.protocol.ProtocolMessage;
import sn.messagerieae.protocol.ProtocolMessage.Command;
import sn.messagerieae.service.AuthService;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectionMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionMonitor.class);

    private static final long PING_INTERVAL_SECONDS = 30;
    private static final long PING_TIMEOUT_SECONDS = 10;

    private final Map<String, ClientHandler> connectedClients;
    private final AuthService authService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "connection-monitor");
        t.setDaemon(true);
        return t;
    });

    public ConnectionMonitor(Map<String, ClientHandler> connectedClients, AuthService authService) {
        this.connectedClients = connectedClients;
        this.authService = authService;
    }

    // ===== Démarrer la surveillance =====
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkConnections,
            PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("Moniteur de connexions démarré (intervalle : {}s)", PING_INTERVAL_SECONDS);
    }

    // ===== Vérifier toutes les connexions =====
    private void checkConnections() {
        connectedClients.forEach((username, handler) -> {
            try {
                if (!handler.isAlive()) {
                    logger.warn("Connexion morte détectée : {}", username);
                    handleDisconnect(username, handler);
                } else {
                    // Envoyer un PING pour vérifier la connexion
                    handler.sendResponse(new ProtocolMessage(Command.SUCCESS, "PING"));
                }
            } catch (Exception e) {
                logger.warn("Échec du ping pour {} : {}", username, e.getMessage());
                handleDisconnect(username, handler);
            }
        });
    }

    // ===== Gérer une déconnexion détectée (RG10) =====
    private void handleDisconnect(String username, ClientHandler handler) {
        authService.forceDisconnect(username);
        connectedClients.remove(username);
        handler.close();

        // Notifier les autres clients
        ProtocolMessage notification = new ProtocolMessage(Command.USER_DISCONNECTED, username);
        notification.setSender(username);
        connectedClients.values().forEach(client -> client.sendResponse(notification));

        logger.info("Client {} retiré après perte de connexion", username);
    }

    // ===== Arrêter la surveillance =====
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Moniteur de connexions arrêté");
    }
}

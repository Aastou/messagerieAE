package sn.messagerieae.service;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.dao.UserDAO;
import sn.messagerieae.model.User;
import sn.messagerieae.model.enums.Role;
import sn.messagerieae.model.enums.Status;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserDAO userDAO;

    // Set thread-safe des usernames connectés (RG3 : session unique)
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // ===== Inscription (RG1 + RG9) =====
    public User register(String username, String password, Role role) {
        // RG1 : username unique
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 4 caractères");
        }
        if (userDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Le nom d'utilisateur '" + username + "' est déjà pris");
        }

        // RG9 : hachage du mot de passe
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User(username, hashedPassword, role);
        User saved = userDAO.save(user);

        logger.info("Inscription réussie : {} (rôle : {})", username, role);
        return saved;
    }

    // ===== Connexion (RG2 + RG3 + RG4) =====
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("Identifiants invalides");
        }

        // Vérifier que l'utilisateur existe
        Optional<User> optUser = userDAO.findByUsername(username);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("Nom d'utilisateur ou mot de passe incorrect");
        }

        User user = optUser.get();

        // Vérifier le mot de passe (RG9)
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new IllegalArgumentException("Nom d'utilisateur ou mot de passe incorrect");
        }

        // RG3 : vérifier session unique
        if (activeSessions.contains(username)) {
            throw new IllegalStateException("L'utilisateur '" + username + "' est déjà connecté");
        }

        // RG4 : passer en ONLINE
        userDAO.updateStatus(user.getId(), Status.ONLINE);
        user.setStatus(Status.ONLINE);
        activeSessions.add(username);

        logger.info("Connexion : {} (rôle : {})", username, user.getRole());
        return user;
    }

    // ===== Déconnexion (RG4) =====
    public void logout(String username) {
        if (username == null) return;

        Optional<User> optUser = userDAO.findByUsername(username);
        optUser.ifPresent(user -> {
            userDAO.updateStatus(user.getId(), Status.OFFLINE);
            activeSessions.remove(username);
            logger.info("Déconnexion : {}", username);
        });
    }

    // ===== Déconnexion forcée (perte de connexion réseau - RG10) =====
    public void forceDisconnect(String username) {
        logger.warn("Perte de connexion détectée pour : {}", username);
        logout(username);
    }

    // ===== Vérifier si un utilisateur est connecté =====
    public boolean isOnline(String username) {
        return activeSessions.contains(username);
    }

    // ===== Accès au set des sessions (pour le serveur) =====
    public Set<String> getActiveSessions() {
        return Set.copyOf(activeSessions);
    }
}

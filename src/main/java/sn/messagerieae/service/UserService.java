package sn.messagerieae.service;

import sn.messagerieae.dao.UserDAO;
import sn.messagerieae.model.User;
import sn.messagerieae.model.enums.Role;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    // ===== Récupérer les utilisateurs connectés =====
    public List<User> getOnlineUsers() {
        return userDAO.findOnlineUsers();
    }

    // ===== Liste complète des membres (RG13 : réservé à ORGANISATEUR) =====
    public List<User> getAllMembers(User requestingUser) {
        if (requestingUser.getRole() != Role.ORGANISATEUR) {
            throw new SecurityException(
                "Seul un ORGANISATEUR peut consulter la liste complète des membres");
        }
        return userDAO.findAll();
    }

    // ===== Rechercher un utilisateur par username =====
    public Optional<User> findByUsername(String username) {
        return userDAO.findByUsername(username);
    }

    // ===== Rechercher un utilisateur par ID =====
    public Optional<User> findById(Long id) {
        return userDAO.findById(id);
    }
}

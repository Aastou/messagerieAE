package sn.messagerieae.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.messagerieae.dao.MessageDAO;
import sn.messagerieae.dao.UserDAO;
import sn.messagerieae.model.Message;
import sn.messagerieae.model.User;
import sn.messagerieae.model.enums.StatutMessage;

import java.util.List;
import java.util.Optional;

public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_CONTENU_LENGTH = 1000;

    private final MessageDAO messageDAO;
    private final UserDAO userDAO;
    private final AuthService authService;

    public MessageService(MessageDAO messageDAO, UserDAO userDAO, AuthService authService) {
        this.messageDAO = messageDAO;
        this.userDAO = userDAO;
        this.authService = authService;
    }

    // ===== Envoyer un message (RG2 + RG5 + RG7) =====
    public Message sendMessage(String senderUsername, String receiverUsername, String contenu) {
        // RG7 : validation du contenu
        validateContenu(contenu);

        // RG5 : l'expéditeur doit être connecté
        if (!authService.isOnline(senderUsername)) {
            throw new IllegalStateException("Vous devez être connecté pour envoyer un message");
        }

        // RG5 : le destinataire doit exister
        Optional<User> optSender = userDAO.findByUsername(senderUsername);
        Optional<User> optReceiver = userDAO.findByUsername(receiverUsername);

        if (optSender.isEmpty()) {
            throw new IllegalArgumentException("Expéditeur introuvable : " + senderUsername);
        }
        if (optReceiver.isEmpty()) {
            throw new IllegalArgumentException("Destinataire introuvable : " + receiverUsername);
        }

        User sender = optSender.get();
        User receiver = optReceiver.get();

        // Créer et sauvegarder le message
        Message message = new Message(sender, receiver, contenu);
        Message saved = messageDAO.save(message);

        logger.info("Message envoyé : {} → {} (id: {})", senderUsername, receiverUsername, saved.getId());
        return saved;
    }

    // ===== Récupérer l'historique d'une conversation (RG8) =====
    public List<Message> getConversation(Long userId1, Long userId2) {
        return messageDAO.findConversation(userId1, userId2);
    }

    // ===== Récupérer les messages non livrés (RG6) =====
    public List<Message> getUndeliveredMessages(Long receiverId) {
        return messageDAO.findUndeliveredMessages(receiverId);
    }

    // ===== Marquer un message comme reçu =====
    public void markAsReceived(Long messageId) {
        messageDAO.updateStatut(messageId, StatutMessage.RECU);
        logger.debug("Message {} marqué comme RECU", messageId);
    }

    // ===== Marquer un message comme lu =====
    public void markAsRead(Long messageId) {
        messageDAO.updateStatut(messageId, StatutMessage.LU);
        logger.debug("Message {} marqué comme LU", messageId);
    }

    // ===== Marquer toute une conversation comme lue =====
    public void markConversationAsRead(Long senderId, Long receiverId) {
        messageDAO.markConversationAsRead(senderId, receiverId);
        logger.debug("Conversation {} → {} marquée comme lue", senderId, receiverId);
    }

    // ===== Compter les messages non lus =====
    public long countUnread(Long senderId, Long receiverId) {
        return messageDAO.countUnreadFrom(senderId, receiverId);
    }

    // ===== Livrer les messages en attente à la reconnexion (RG6) =====
    public List<Message> deliverPendingMessages(Long receiverId) {
        List<Message> pending = messageDAO.findUndeliveredMessages(receiverId);
        for (Message msg : pending) {
            messageDAO.updateStatut(msg.getId(), StatutMessage.RECU);
        }
        if (!pending.isEmpty()) {
            logger.info("{} message(s) en attente livré(s) à l'utilisateur id={}", pending.size(), receiverId);
        }
        return pending;
    }

    // ===== Validation du contenu (RG7) =====
    private void validateContenu(String contenu) {
        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException("Le contenu du message ne peut pas être vide");
        }
        if (contenu.length() > MAX_CONTENU_LENGTH) {
            throw new IllegalArgumentException(
                "Le contenu du message ne doit pas dépasser " + MAX_CONTENU_LENGTH + " caractères");
        }
    }
}

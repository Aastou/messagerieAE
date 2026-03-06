package sn.messagerieae.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import sn.messagerieae.model.Message;
import sn.messagerieae.model.enums.StatutMessage;
import sn.messagerieae.util.JpaUtil;

import java.util.List;

public class MessageDAO {

    // ===== Sauvegarder un message =====
    public Message save(Message message) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(message);
            tx.commit();
            return message;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Erreur lors de la sauvegarde du message", e);
        } finally {
            em.close();
        }
    }

    // ===== Historique entre deux utilisateurs (RG8 - ordre chronologique) =====
    public List<Message> findConversation(Long userId1, Long userId2) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m " +
                        "WHERE (m.sender.id = :u1 AND m.receiver.id = :u2) " +
                        "   OR (m.sender.id = :u2 AND m.receiver.id = :u1) " +
                        "ORDER BY m.dateEnvoi ASC", Message.class)
                .setParameter("u1", userId1)
                .setParameter("u2", userId2)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // ===== Messages non livrés pour un utilisateur (RG6) =====
    public List<Message> findUndeliveredMessages(Long receiverId) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m " +
                        "WHERE m.receiver.id = :receiverId " +
                        "  AND m.statut = :statut " +
                        "ORDER BY m.dateEnvoi ASC", Message.class)
                .setParameter("receiverId", receiverId)
                .setParameter("statut", StatutMessage.ENVOYE)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // ===== Mettre à jour le statut d'un message (ENVOYE → RECU → LU) =====
    public void updateStatut(Long messageId, StatutMessage statut) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery(
                    "UPDATE Message m SET m.statut = :statut WHERE m.id = :id")
                .setParameter("statut", statut)
                .setParameter("id", messageId)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Erreur lors de la mise à jour du statut du message", e);
        } finally {
            em.close();
        }
    }

    // ===== Marquer tous les messages d'une conversation comme lus =====
    public void markConversationAsRead(Long senderId, Long receiverId) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery(
                    "UPDATE Message m SET m.statut = :newStatut " +
                        "WHERE m.sender.id = :senderId " +
                        "  AND m.receiver.id = :receiverId " +
                        "  AND m.statut != :newStatut")
                .setParameter("newStatut", StatutMessage.LU)
                .setParameter("senderId", senderId)
                .setParameter("receiverId", receiverId)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Erreur lors du marquage des messages comme lus", e);
        } finally {
            em.close();
        }
    }

    // ===== Compter les messages non lus d'un expéditeur =====
    public long countUnreadFrom(Long senderId, Long receiverId) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT COUNT(m) FROM Message m " +
                        "WHERE m.sender.id = :senderId " +
                        "  AND m.receiver.id = :receiverId " +
                        "  AND m.statut != :lu", Long.class)
                .setParameter("senderId", senderId)
                .setParameter("receiverId", receiverId)
                .setParameter("lu", StatutMessage.LU)
                .getSingleResult();
        } finally {
            em.close();
        }
    }
}

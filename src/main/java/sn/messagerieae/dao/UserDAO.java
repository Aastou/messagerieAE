package sn.messagerieae.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import sn.messagerieae.model.User;
import sn.messagerieae.model.enums.Status;
import sn.messagerieae.util.JpaUtil;

import java.util.List;
import java.util.Optional;

public class UserDAO {

    // ===== Créer un utilisateur =====
    public User save(User user) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(user);
            tx.commit();
            return user;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Erreur lors de la création de l'utilisateur", e);
        } finally {
            em.close();
        }
    }

    // ===== Rechercher par ID =====
    public Optional<User> findById(Long id) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(User.class, id));
        } finally {
            em.close();
        }
    }

    // ===== Rechercher par username =====
    public Optional<User> findByUsername(String username) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            User user = em.createQuery(
                    "SELECT u FROM User u WHERE u.username = :username", User.class)
                .setParameter("username", username)
                .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }

    // ===== Vérifier si un username existe (RG1) =====
    public boolean existsByUsername(String username) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            Long count = em.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult();
            return count > 0;
        } finally {
            em.close();
        }
    }

    // ===== Mettre à jour le statut (RG4) =====
    public void updateStatus(Long userId, Status status) {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery(
                    "UPDATE User u SET u.status = :status WHERE u.id = :id")
                .setParameter("status", status)
                .setParameter("id", userId)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Erreur lors de la mise à jour du statut", e);
        } finally {
            em.close();
        }
    }

    // ===== Récupérer les utilisateurs en ligne =====
    public List<User> findOnlineUsers() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT u FROM User u WHERE u.status = :status ORDER BY u.username", User.class)
                .setParameter("status", Status.ONLINE)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // ===== Liste complète des membres (RG13 - pour ORGANISATEUR) =====
    public List<User> findAll() {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT u FROM User u ORDER BY u.dateCreation DESC", User.class)
                .getResultList();
        } finally {
            em.close();
        }
    }
}

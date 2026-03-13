# MessagerieAE

Application de messagerie instantanée pour une association, développée en Java avec une architecture client-serveur TCP.

## Stack technique

| Couche | Technologie |
|--------|-------------|
| Interface | JavaFX 21 + FXML |
| Réseau | Sockets TCP (port 5555) |
| Protocole | JSON via Gson |
| Persistance | Hibernate 6 / JPA + PostgreSQL |
| Sécurité | BCrypt (hachage mots de passe) |
| Logs | SLF4J + Logback |
| Build | Maven |
| Java | 17 |

## Architecture

```
messagerieAE/
├── server/
│   ├── Server.java          # Serveur TCP (port 5555), un thread par client
│   └── ClientHandler.java   # Traitement des commandes par client
├── client/
│   ├── ClientSocket.java    # Singleton — connexion TCP, envoi/réception
│   ├── SceneManager.java    # Navigation entre les vues JavaFX
│   └── MessageListener.java # Interface de callback
├── controller/
│   ├── LoginController.java
│   ├── RegisterController.java
│   └── ChatController.java
├── service/
│   ├── AuthService.java     # Inscription, connexion, sessions
│   ├── UserService.java
│   └── MessageService.java
├── dao/
│   ├── UserDAO.java
│   └── MessageDAO.java
├── model/
│   ├── User.java
│   ├── Message.java
│   └── enums/  (Role, Status, StatutMessage)
└── protocol/
    └── ProtocolMessage.java # Format d'échange JSON client-serveur
```

## Prérequis

- Java 17+
- Maven 3.8+
- PostgreSQL (base de données configurée dans `persistence.xml`)

## Lancer l'application

### 1. Démarrer le serveur

```bash
mvn exec:java
```

Le serveur écoute sur le port **5555**.

### 2. Démarrer le client JavaFX

```bash
mvn javafx:run
```

Le client se connecte automatiquement à `localhost:5555`.

## Fonctionnalités

- **Inscription** — création de compte avec rôle (MEMBRE, BENEVOLE, ORGANISATEUR)
- **Connexion** — authentification BCrypt, session unique par utilisateur (RG3)
- **Chat en temps réel** — messagerie instantanée entre utilisateurs connectés
- **Messages en attente** — livraison automatique à la reconnexion (RG6)
- **Historique** — consultation des conversations passées (RG8)
- **Utilisateurs en ligne** — liste mise à jour en temps réel
- **Gestion des membres** — vue réservée aux ORGANISATEURS (RG13)
- **Déconnexion propre** — logout explicite ou fermeture de fenêtre

## Rôles

| Rôle | Accès |
|------|-------|
| `MEMBRE` | Chat, historique |
| `BENEVOLE` | Chat, historique |
| `ORGANISATEUR` | Chat, historique, liste complète des membres |

## Protocole réseau

Les échanges client-serveur utilisent des messages JSON sur socket TCP :

```json
{
  "command": "LOGIN",
  "sender": "alice",
  "content": "motdepasse",
  "role": null,
  "receiver": null,
  "extra": null
}
```

**Commandes client → serveur** : `LOGIN`, `REGISTER`, `LOGOUT`, `SEND_MESSAGE`, `GET_HISTORY`, `GET_ONLINE_USERS`, `GET_ALL_MEMBERS`

**Réponses serveur → client** : `SUCCESS`, `ERROR`, `INCOMING_MESSAGE`, `MESSAGE_HISTORY`, `USER_LIST`, `USER_CONNECTED`, `USER_DISCONNECTED`
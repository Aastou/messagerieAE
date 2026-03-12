package sn.messagerieae.client;

import sn.messagerieae.protocol.ProtocolMessage;

/** Interface implémentée par les contrôleurs qui écoutent les messages du serveur. */
@FunctionalInterface
public interface MessageListener {
    /** Appelé sur le thread JavaFX à chaque message reçu du serveur. */
    void onMessage(ProtocolMessage message);
}

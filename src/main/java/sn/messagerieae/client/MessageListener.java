package sn.messagerieae.client;

import sn.messagerieae.protocol.ProtocolMessage;

@FunctionalInterface
public interface MessageListener {
    void onMessage(ProtocolMessage message);
}

package org.objectspace.wsbrokerclient.client;

/**
 * Listener interface for receiving connection state transition notifications.
 */
@FunctionalInterface
public interface ConnectionListener {

    /**
     * Invoked when the client connection state transitions.
     *
     * @param oldState previous state
     * @param newState current state
     */
    void onStateChanged(ConnectionState oldState, ConnectionState newState);
}

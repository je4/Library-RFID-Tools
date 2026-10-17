package org.objectspace.wsbrokerclient.client;

/**
 * State of the WSBroker WebSocket connection.
 */
public enum ConnectionState {

    /**
     * Client is disconnected from the broker.
     */
    DISCONNECTED,

    /**
     * Initial connection attempt in progress.
     */
    CONNECTING,

    /**
     * WebSocket connection established and active.
     */
    CONNECTED,

    /**
     * Reconnection loop in progress after a connection loss.
     */
    RECONNECTING,

    /**
     * Client is gracefully closing.
     */
    CLOSING,

    /**
     * Client has been closed and resources released.
     */
    CLOSED
}

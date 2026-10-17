package org.objectspace.wsbrokerclient.event;

/**
 * Functional interface for handling received WSBroker events with strongly typed data payloads.
 *
 * @param <T> payload type
 */
@FunctionalInterface
public interface TypedEventHandler<T> {

    /**
     * Handles the received event and its deserialized payload.
     *
     * @param event the received event envelope
     * @param data  the deserialized data payload
     * @throws Exception if handler processing fails
     */
    void onEvent(Event event, T data) throws Exception;
}

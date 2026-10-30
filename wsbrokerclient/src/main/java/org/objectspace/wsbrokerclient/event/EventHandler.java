package org.objectspace.wsbrokerclient.event;

/**
 * Functional interface for handling received WSBroker events.
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Handles the received event.
     *
     * @param event the received event envelope
     * @throws Exception if handler processing fails
     */
    void onEvent(Event<?> event) throws Exception;
}

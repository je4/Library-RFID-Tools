package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Standard event types used across the WSBroker ecosystem.
 */
public enum EventType {

    /**
     * Heartbeat ping event.
     */
    CLIENT_PING("ping"),

    /**
     * Heartbeat pong response.
     */
    CLIENT_PONG("pong"),

    /**
     * Standard message payload event.
     */
    CLIENT_MESSAGE("message"),

    /**
     * Binary or document file transfer payload event.
     */
    CLIENT_FILE("file"),

    /**
     * Dynamic channel attach / subscribe event.
     */
    CLIENT_ATTACH("attach"),

    /**
     * Dynamic channel detach / unsubscribe event.
     */
    CLIENT_DETACH("detach"),

    /**
     * Remote action invocation event.
     */
    CLIENT_ACTION("action"),

    /**
     * Client or node status announcement.
     */
    CLIENT_STATUS("status"),

    /**
     * Client runtime metadata or capability information.
     */
    CLIENT_INFO("info"),

    /**
     * Broadcast message dispatched to all connected clients.
     */
    CLIENT_BROADCAST("broadcast"),

    /**
     * NTP time synchronization query over WebSocket.
     */
    CLIENT_NTP_QUERY("ntp-query"),

    /**
     * NTP time synchronization response over WebSocket.
     */
    CLIENT_NTP_RESPONSE("ntp-response"),

    /**
     * NTP synchronization error event.
     */
    CLIENT_NTP_ERROR("ntp-error"),

    /**
     * Administrative or control event.
     */
    CLIENT_CONTROL("control"),

    /**
     * Broker system-level event.
     */
    CLIENT_SYSTEM("system");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    /**
     * Gets the raw string representation of the event type.
     *
     * @return the event type string
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Resolves an EventType from its string value.
     *
     * @param value the string representation
     * @return matching EventType, or null if unknown
     */
    @JsonCreator
    public static EventType fromString(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        for (EventType type : values()) {
            if (type.value.equalsIgnoreCase(clean)) {
                return type;
            }
        }
        // Fallback / legacy alias matching:
        // Support prefixes like "client:" (e.g. "client:ping", "client:ntp:query", "client:message")
        // and alternative delimiters (e.g. "ntp:query", "ntp_query")
        String normalized = clean.toLowerCase();
        if (normalized.startsWith("client:")) {
            normalized = normalized.substring(7);
        }
        normalized = normalized.replace(':', '-').replace('_', '-');

        for (EventType type : values()) {
            if (type.value.equalsIgnoreCase(normalized)) {
                return type;
            }
        }

        return null;
    }

    /**
     * Checks if this enum matches a given event type string (case-insensitively).
     *
     * @param typeString the type string to compare
     * @return true if matching
     */
    public boolean matches(String typeString) {
        if (typeString == null) {
            return false;
        }
        if (value.equalsIgnoreCase(typeString.trim())) {
            return true;
        }
        return fromString(typeString) == this;
    }
}

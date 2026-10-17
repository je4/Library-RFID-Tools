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
    CLIENT_PING("client:ping"),

    /**
     * Heartbeat pong response.
     */
    CLIENT_PONG("client:pong"),

    /**
     * Standard message payload event.
     */
    CLIENT_MESSAGE("client:message"),

    /**
     * Remote action invocation event.
     */
    CLIENT_ACTION("client:action"),

    /**
     * Client or node status announcement.
     */
    CLIENT_STATUS("client:status"),

    /**
     * Client runtime metadata or capability information.
     */
    CLIENT_INFO("client:info"),

    /**
     * Broadcast message dispatched to all connected clients.
     */
    CLIENT_BROADCAST("client:broadcast"),

    /**
     * NTP time synchronization query over WebSocket.
     */
    CLIENT_NTP_QUERY("client:ntp:query"),

    /**
     * NTP time synchronization response over WebSocket.
     */
    CLIENT_NTP_RESPONSE("client:ntp:response"),

    /**
     * NTP synchronization error event.
     */
    CLIENT_NTP_ERROR("client:ntp:error"),

    /**
     * Administrative or control event.
     */
    CLIENT_CONTROL("client:control"),

    /**
     * Broker system-level event.
     */
    CLIENT_SYSTEM("client:system");

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
        for (EventType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
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
        return typeString != null && value.equalsIgnoreCase(typeString.trim());
    }
}

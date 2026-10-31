package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.objectspace.wsbrokerclient.rest.ChannelInfo;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping event type identifiers to concrete Java classes or custom payload unmarshalers.
 * Aligns with Go wsbrokerclient payload registration mechanism.
 */
public final class PayloadRegistry {

    @FunctionalInterface
    public interface PayloadUnmarshaler {
        Object unmarshal(JsonNode dataNode, ObjectMapper mapper) throws Exception;
    }

    private record Registration(Class<?> targetClass, PayloadUnmarshaler unmarshaler) {}

    private static final Map<String, Registration> REGISTRY = new ConcurrentHashMap<>();

    static {
        // Register built-in standard types
        register(EventType.CLIENT_NTP_QUERY, byte[].class);
        register(EventType.CLIENT_NTP_RESPONSE, byte[].class);
        register(EventType.CLIENT_NTP_ERROR, String.class);
        register(EventType.CLIENT_INFO, ChannelInfo.class);
    }

    private PayloadRegistry() {
    }

    /**
     * Normalizes event type string for registry storage and lookup.
     */
    public static String normalizeType(String eventType) {
        if (eventType == null) {
            return "";
        }
        return eventType.trim().toLowerCase();
    }

    /**
     * Registers a concrete payload class for a given event type string.
     *
     * @param eventType the event type string (e.g. "rfid/tag_read")
     * @param clazz     the target Java class
     */
    public static void register(String eventType, Class<?> clazz) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(clazz, "clazz cannot be null");
        String key = normalizeType(eventType);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("eventType cannot be empty");
        }
        REGISTRY.put(key, new Registration(clazz, (node, mapper) -> {
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (clazz == String.class && node.isTextual()) {
                return node.asText();
            }
            return mapper.treeToValue(node, clazz);
        }));
    }

    /**
     * Registers a concrete payload class for a standard EventType enum.
     *
     * @param type  the standard EventType
     * @param clazz the target Java class
     */
    public static void register(EventType type, Class<?> clazz) {
        Objects.requireNonNull(type, "type cannot be null");
        register(type.getValue(), clazz);
    }

    /**
     * Registers a custom unmarshaler function for a given event type string.
     *
     * @param eventType   the event type string
     * @param targetClass the target class (optional, may be null)
     * @param unmarshaler the unmarshaler function
     */
    public static void registerFactory(String eventType, Class<?> targetClass, PayloadUnmarshaler unmarshaler) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(unmarshaler, "unmarshaler cannot be null");
        String key = normalizeType(eventType);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("eventType cannot be empty");
        }
        REGISTRY.put(key, new Registration(targetClass, unmarshaler));
    }

    /**
     * Checks if an event type is registered in the payload registry.
     */
    public static boolean isRegistered(String eventType) {
        if (eventType == null) {
            return false;
        }
        return REGISTRY.containsKey(normalizeType(eventType));
    }

    /**
     * Checks if a standard EventType is registered.
     */
    public static boolean isRegistered(EventType type) {
        return type != null && isRegistered(type.getValue());
    }

    /**
     * Gets the registered target class for an event type, if known.
     */
    public static Class<?> getPayloadClass(String eventType) {
        if (eventType == null) {
            return null;
        }
        Registration reg = REGISTRY.get(normalizeType(eventType));
        return reg != null ? reg.targetClass() : null;
    }

    /**
     * Unmarshals a raw JSON node according to the registration for the given event type.
     *
     * @param eventType event type identifier
     * @param node      raw JSON node
     * @param mapper    object mapper instance
     * @return unmarshaled payload object, or null/node if unregistered
     */
    public static Object unmarshal(String eventType, JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (eventType != null) {
            Registration reg = REGISTRY.get(normalizeType(eventType));
            if (reg != null && reg.unmarshaler() != null) {
                try {
                    return reg.unmarshaler().unmarshal(node, mapper);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to unmarshal payload for event type '" + eventType + "'", e);
                }
            }
        }
        // Fallback: If text node, return text string; otherwise return raw node or map
        if (node.isTextual()) {
            return node.asText();
        }
        return node;
    }

    /**
     * Dynamically unmarshals a RawEvent's data payload according to the registered event type.
     *
     * @param rawEvent raw event envelope
     * @return decoded payload object, or JsonNode/String if unregistered
     */
    public static Object unmarshalDynamic(RawEvent rawEvent) {
        if (rawEvent == null) {
            return null;
        }
        return unmarshal(rawEvent.getType(), rawEvent.getData(), BaseEvent.OBJECT_MAPPER);
    }

    /**
     * Clears all registered custom event types (resets to defaults).
     */
    public static void resetToDefaults() {
        REGISTRY.clear();
        register(EventType.CLIENT_NTP_QUERY, byte[].class);
        register(EventType.CLIENT_NTP_RESPONSE, byte[].class);
        register(EventType.CLIENT_NTP_ERROR, String.class);
        register(EventType.CLIENT_INFO, ChannelInfo.class);
    }
}

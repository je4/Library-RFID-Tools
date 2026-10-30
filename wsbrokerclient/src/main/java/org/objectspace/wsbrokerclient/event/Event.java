package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic event message envelope carrying a strongly-typed or dynamic data payload.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event<T> extends BaseEvent {

    @JsonProperty("data")
    private T data;

    public Event() {
        super();
    }

    public Event(String type, String source, String target) {
        super(type, source, target);
    }

    public Event(String type, String source, String target, T data) {
        super(type, source, target);
        this.data = data;
    }

    public T getData() {
        return data;
    }

    @JsonIgnore
    public JsonNode getDataNode() {
        return getDataAs(JsonNode.class);
    }

    @JsonProperty("data")
    public Event<T> setData(T data) {
        this.data = data;
        return this;
    }

    /**
     * Convenience factory for creating a typed Event instance.
     *
     * @param type   event type
     * @param source source identifier
     * @param target target identifier or channel
     * @param data   typed payload
     * @param <T>    payload type
     * @return typed Event instance
     */
    public static <T> Event<T> of(String type, String source, String target, T data) {
        return new Event<>(type, source, target, data);
    }

    /**
     * Deserializes the event's data payload into the specified target class.
     *
     * @param targetClass target class type
     * @param <U>         return type
     * @return deserialized object, or null if data is null/empty
     * @throws IllegalArgumentException if JSON mapping fails
     */
    public <U> U getDataAs(Class<U> targetClass) {
        if (data == null) {
            return null;
        }
        if (targetClass.isInstance(data)) {
            return targetClass.cast(data);
        }
        if (data instanceof JsonNode node) {
            if (node.isNull() || node.isMissingNode()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.treeToValue(node, targetClass);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to deserialize event data to " + targetClass.getName(), e);
            }
        }
        return OBJECT_MAPPER.convertValue(data, targetClass);
    }

    /**
     * Deserializes the event's data payload using a Jackson TypeReference (useful for generics/collections).
     *
     * @param typeReference target type reference
     * @param <U>           return type
     * @return deserialized object, or null if data is null/empty
     * @throws IllegalArgumentException if JSON mapping fails
     */
    public <U> U getDataAs(TypeReference<U> typeReference) {
        if (data == null) {
            return null;
        }
        if (data instanceof JsonNode node) {
            if (node.isNull() || node.isMissingNode()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(OBJECT_MAPPER.treeAsTokens(node), typeReference);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to deserialize event data to type reference", e);
            }
        }
        return OBJECT_MAPPER.convertValue(data, typeReference);
    }

    /**
     * Returns the event data payload as a string (unquoted text if textual, UTF-8 string if byte array, or JSON representation otherwise).
     *
     * @return string representation or null
     */
    @JsonIgnore
    public String getDataAsString() {
        if (data == null) {
            return null;
        }
        if (data instanceof String str) {
            return str;
        }
        if (data instanceof JsonNode node) {
            if (node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            return node.toString();
        }
        if (data instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }

    // --- Static Factory / Builder Methods ---

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static Event<String> createPing(String source) {
        return Event.<String>builder()
                .type(EventType.CLIENT_PING)
                .source(source)
                .data("ping")
                .build();
    }

    public static Event<String> createPong(String source, String target) {
        return createPong(source, target, null);
    }

    public static Event<String> createPong(String source, String target, String refId) {
        return Event.<String>builder()
                .type(EventType.CLIENT_PONG)
                .source(source)
                .target(target != null && !target.isBlank() ? target : "wsbroker")
                .refId(refId)
                .data("pong")
                .build();
    }

    public static Event<String> createAttach(String source, String channel) {
        return Event.<String>builder()
                .type(EventType.CLIENT_ATTACH)
                .source(source)
                .target(channel)
                .data(channel)
                .build();
    }

    public static Event<String> createDetach(String source, String channel) {
        return Event.<String>builder()
                .type(EventType.CLIENT_DETACH)
                .source(source)
                .target(channel)
                .data(channel)
                .build();
    }

    public static Event<byte[]> createNtpQuery(String source, byte[] ntpBytes) {
        return Event.<byte[]>builder()
                .type(EventType.CLIENT_NTP_QUERY)
                .source(source)
                .data(ntpBytes)
                .build();
    }

    public static <T> Event<T> createMessage(String source, String target, T payload) {
        return Event.<T>builder()
                .type(EventType.CLIENT_MESSAGE)
                .source(source)
                .target(target)
                .data(payload)
                .build();
    }

    public static <T> Event<T> createAction(String source, String target, String action, T payload) {
        Event<T> event = Event.<T>builder()
                .type(EventType.CLIENT_ACTION)
                .source(source)
                .target(target)
                .data(payload)
                .build();
        if (action != null && !action.isBlank()) {
            event.addAction(action);
        }
        return event;
    }

    public static <T> Event<T> createBroadcast(String source, T payload) {
        return Event.<T>builder()
                .type(EventType.CLIENT_BROADCAST)
                .source(source)
                .data(payload)
                .build();
    }

    public static Event<Void> createStatus(String source, String statusMessage) {
        return Event.<Void>builder()
                .type(EventType.CLIENT_STATUS)
                .source(source)
                .message(statusMessage)
                .build();
    }

    public static <T> Event<T> createInfo(String source, T infoPayload) {
        return Event.<T>builder()
                .type(EventType.CLIENT_INFO)
                .source(source)
                .data(infoPayload)
                .build();
    }

    /**
     * Static helper to create a reply correlated with a given request Event.
     *
     * @param request   the initiating request event
     * @param replyType event type of the reply
     * @param payload   data payload for the reply
     * @param <R>       reply payload type
     * @return reply Event instance
     */
    public static <R> Event<R> createReply(BaseEvent request, EventType replyType, R payload) {
        if (request == null) {
            throw new IllegalArgumentException("Request event cannot be null");
        }
        return request.createReply(replyType, payload);
    }

    /**
     * Static helper to create a reply correlated with a given request Event.
     *
     * @param request   the initiating request event
     * @param replyType string representation of event type
     * @param payload   data payload for the reply
     * @param <R>       reply payload type
     * @return reply Event instance
     */
    public static <R> Event<R> createReply(BaseEvent request, String replyType, R payload) {
        if (request == null) {
            throw new IllegalArgumentException("Request event cannot be null");
        }
        return request.createReply(replyType, payload);
    }

    /**
     * Deserializes an Event from JSON string dynamically (with JsonNode data payload as RawEvent).
     */
    public static RawEvent fromJson(String json) {
        return RawEvent.fromJson(json);
    }

    /**
     * Deserializes a strongly-typed Event from JSON string.
     */
    public static <T> Event<T> fromJson(String json, Class<T> payloadClass) {
        try {
            JavaType type = OBJECT_MAPPER.getTypeFactory().constructParametricType(Event.class, payloadClass);
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize Event from JSON: " + json, e);
        }
    }

    /**
     * Deserializes a strongly-typed Event from JSON string with TypeReference.
     */
    public static <T> Event<T> fromJson(String json, TypeReference<T> typeReference) {
        try {
            JavaType innerType = OBJECT_MAPPER.getTypeFactory().constructType(typeReference);
            JavaType eventType = OBJECT_MAPPER.getTypeFactory().constructParametricType(Event.class, innerType);
            return OBJECT_MAPPER.readValue(json, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize Event from JSON: " + json, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event<?> event)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(data, event.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data);
    }

    @Override
    public String toString() {
        return "Event{" +
                "id='" + getId() + '\'' +
                ", refId='" + getRefId() + '\'' +
                ", channel='" + getChannel() + '\'' +
                ", time=" + getTime() +
                ", source='" + getSource() + '\'' +
                ", target='" + getTarget() + '\'' +
                ", type='" + getType() + '\'' +
                ", session='" + getSession() + '\'' +
                ", data=" + data +
                ", message='" + getMessage() + '\'' +
                ", error='" + getError() + '\'' +
                ", actions=" + getActions() +
                '}';
    }

    public static class Builder<T> {
        private final Event<T> event = new Event<>();

        public Builder<T> id(String id) {
            event.setId(id);
            return this;
        }

        public Builder<T> refId(String refId) {
            event.setRefId(refId);
            return this;
        }

        public Builder<T> channel(String channel) {
            event.setChannel(channel);
            return this;
        }

        public Builder<T> time(Instant time) {
            event.setTime(time);
            return this;
        }

        public Builder<T> source(String source) {
            event.setSource(source);
            return this;
        }

        public Builder<T> target(String target) {
            event.setTarget(target);
            return this;
        }

        public Builder<T> type(String type) {
            event.setType(type);
            return this;
        }

        public Builder<T> type(EventType type) {
            event.setType(type);
            return this;
        }

        public Builder<T> session(String session) {
            event.setSession(session);
            return this;
        }

        public Builder<T> data(T data) {
            event.setData(data);
            return this;
        }

        public Builder<T> message(String message) {
            event.setMessage(message);
            return this;
        }

        public Builder<T> error(String error) {
            event.setError(error);
            return this;
        }

        public Builder<T> actions(List<String> actions) {
            event.setActions(actions != null ? new ArrayList<>(actions) : null);
            return this;
        }

        public Builder<T> addAction(String action) {
            event.addAction(action);
            return this;
        }

        public Builder<T> subprotocols(List<String> subprotocols) {
            event.setSubprotocols(subprotocols != null ? new ArrayList<>(subprotocols) : null);
            return this;
        }

        public Event<T> build() {
            return event;
        }
    }
}

package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Standard event message envelope transferred between clients and the WSBroker.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically secure, random 16-byte (32-character hexadecimal) unique event identifier.
     *
     * @return 32-character lowercase hex string
     */
    public static String generateEventId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @JsonProperty("id")
    private String id;

    @JsonProperty("ref_id")
    private String refId;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("time")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant time = Instant.now();

    @JsonProperty("source")
    private String source;

    @JsonProperty("target")
    private String target;

    @JsonProperty("type")
    private String type;

    @JsonProperty("token")
    private String token;

    @JsonProperty("data")
    private JsonNode data;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error")
    private String error;

    @JsonProperty("actions")
    private List<String> actions;

    @JsonProperty("subprotocols")
    private List<String> subprotocols;

    public Event() {
        this.id = generateEventId();
    }

    public Event(String type, String source, String target) {
        this.id = generateEventId();
        this.time = Instant.now();
        this.type = type;
        this.source = source;
        this.target = target;
    }

    public String getId() {
        return id;
    }

    public Event setId(String id) {
        this.id = id;
        return this;
    }

    public String getRefId() {
        return refId;
    }

    public Event setRefId(String refId) {
        this.refId = refId;
        return this;
    }

    public String getChannel() {
        return channel;
    }

    public Event setChannel(String channel) {
        this.channel = channel;
        return this;
    }

    public Instant getTime() {
        return time;
    }

    public Event setTime(Instant time) {
        this.time = time;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Event setSource(String source) {
        this.source = source;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public Event setTarget(String target) {
        this.target = target;
        return this;
    }

    public String getType() {
        return type;
    }

    public Event setType(String type) {
        this.type = type;
        return this;
    }

    public Event setType(EventType eventType) {
        this.type = eventType != null ? eventType.getValue() : null;
        return this;
    }

    @JsonIgnore
    public EventType getEventType() {
        return EventType.fromString(type);
    }

    public boolean isType(EventType eventType) {
        return eventType != null && eventType.matches(type);
    }

    public boolean isType(String typeString) {
        return typeString != null && typeString.equalsIgnoreCase(type);
    }

    public String getToken() {
        return token;
    }

    public Event setToken(String token) {
        this.token = token;
        return this;
    }

    public JsonNode getData() {
        return data;
    }

    @JsonProperty("data")
    public Event setData(JsonNode data) {
        this.data = data;
        return this;
    }

    @JsonIgnore
    public Event setData(Object object) {
        if (object == null) {
            this.data = null;
        } else if (object instanceof JsonNode node) {
            this.data = node;
        } else {
            this.data = OBJECT_MAPPER.valueToTree(object);
        }
        return this;
    }

    /**
     * Deserializes the event's data payload into the specified target class.
     *
     * @param targetClass target class type
     * @param <T>         return type
     * @return deserialized object, or null if data is null/empty
     * @throws IllegalArgumentException if JSON mapping fails
     */
    public <T> T getDataAs(Class<T> targetClass) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(data, targetClass);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize event data to " + targetClass.getName(), e);
        }
    }

    /**
     * Deserializes the event's data payload using a Jackson TypeReference (useful for generics/collections).
     *
     * @param typeReference target type reference
     * @param <T>           return type
     * @return deserialized object, or null if data is null/empty
     * @throws IllegalArgumentException if JSON mapping fails
     */
    public <T> T getDataAs(TypeReference<T> typeReference) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(OBJECT_MAPPER.treeAsTokens(data), typeReference);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize event data to type reference", e);
        }
    }

    /**
     * Returns the event data payload as a string (unquoted text if textual, or JSON representation otherwise).
     *
     * @return string representation or null
     */
    @JsonIgnore
    public String getDataAsString() {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        if (data.isTextual()) {
            return data.asText();
        }
        return data.toString();
    }

    public String getMessage() {
        return message;
    }

    public Event setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getError() {
        return error;
    }

    public Event setError(String error) {
        this.error = error;
        return this;
    }

    public List<String> getActions() {
        return actions;
    }

    public Event setActions(List<String> actions) {
        this.actions = actions;
        return this;
    }

    public Event addAction(String action) {
        if (this.actions == null) {
            this.actions = new ArrayList<>();
        }
        this.actions.add(action);
        return this;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public Event setSubprotocols(List<String> subprotocols) {
        this.subprotocols = subprotocols;
        return this;
    }

    // --- Static Factory / Builder Methods ---

    public static Builder builder() {
        return new Builder();
    }

    public static Event createPing(String source) {
        return builder()
                .type(EventType.CLIENT_PING)
                .source(source)
                .data("ping")
                .build();
    }

    public static Event createPong(String source, String target) {
        return createPong(source, target, null, null);
    }

    public static Event createPong(String source, String target, String token) {
        return createPong(source, target, token, null);
    }

    public static Event createPong(String source, String target, String token, String refId) {
        return builder()
                .type(EventType.CLIENT_PONG)
                .source(source)
                .target(target != null && !target.isBlank() ? target : "wsbroker")
                .token(token)
                .refId(refId)
                .data("pong")
                .build();
    }

    public static Event createAttach(String source, String channel) {
        return builder()
                .type(EventType.CLIENT_ATTACH)
                .source(source)
                .target(channel)
                .data(channel)
                .build();
    }

    public static Event createDetach(String source, String channel) {
        return builder()
                .type(EventType.CLIENT_DETACH)
                .source(source)
                .target(channel)
                .data(channel)
                .build();
    }

    public static Event createNtpQuery(String source, byte[] ntpBytes) {
        return builder()
                .type(EventType.CLIENT_NTP_QUERY)
                .source(source)
                .data(ntpBytes)
                .build();
    }

    public static Event createMessage(String source, String target, Object payload) {
        return builder()
                .type(EventType.CLIENT_MESSAGE)
                .source(source)
                .target(target)
                .data(payload)
                .build();
    }

    public static Event createAction(String source, String target, String action, Object payload) {
        Event event = builder()
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

    public static Event createBroadcast(String source, Object payload) {
        return builder()
                .type(EventType.CLIENT_BROADCAST)
                .source(source)
                .data(payload)
                .build();
    }

    public static Event createStatus(String source, String statusMessage) {
        return builder()
                .type(EventType.CLIENT_STATUS)
                .source(source)
                .message(statusMessage)
                .build();
    }

    public static Event createInfo(String source, Object infoPayload) {
        return builder()
                .type(EventType.CLIENT_INFO)
                .source(source)
                .data(infoPayload)
                .build();
    }

    /**
     * Creates a reply Event correlated with this request Event.
     * Sets {@code id} to a newly generated unique identifier, {@code ref_id} to this event's {@code id},
     * {@code target} to this event's {@code source} (or channel/target as fallback), and copies over {@code channel} and {@code token}.
     *
     * @param replyType event type of the reply
     * @param payload   data payload for the reply
     * @return reply Event instance
     */
    public Event createReply(EventType replyType, Object payload) {
        return createReply(replyType != null ? replyType.getValue() : null, payload);
    }

    /**
     * Creates a reply Event correlated with this request Event.
     * Sets {@code id} to a newly generated unique identifier, {@code ref_id} to this event's {@code id},
     * {@code target} to this event's {@code source} (or channel/target as fallback), and copies over {@code channel} and {@code token}.
     *
     * @param replyType string representation of event type
     * @param payload   data payload for the reply
     * @return reply Event instance
     */
    public Event createReply(String replyType, Object payload) {
        String replyTarget = this.source;
        if (replyTarget == null || replyTarget.isBlank()) {
            replyTarget = this.channel;
        }
        if (replyTarget == null || replyTarget.isBlank()) {
            replyTarget = this.target;
        }
        return builder()
                .type(replyType)
                .target(replyTarget)
                .channel(this.channel)
                .token(this.token)
                .refId(this.id)
                .data(payload)
                .build();
    }

    /**
     * Static helper to create a reply correlated with a given request Event.
     *
     * @param request   the initiating request event
     * @param replyType event type of the reply
     * @param payload   data payload for the reply
     * @return reply Event instance
     */
    public static Event createReply(Event request, EventType replyType, Object payload) {
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
     * @return reply Event instance
     */
    public static Event createReply(Event request, String replyType, Object payload) {
        if (request == null) {
            throw new IllegalArgumentException("Request event cannot be null");
        }
        return request.createReply(replyType, payload);
    }

    /**
     * Serializes this Event instance to JSON string.
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Event to JSON", e);
        }
    }

    /**
     * Deserializes an Event from JSON string.
     */
    public static Event fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Event.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize Event from JSON: " + json, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event event)) return false;
        return Objects.equals(id, event.id) &&
                Objects.equals(refId, event.refId) &&
                Objects.equals(channel, event.channel) &&
                Objects.equals(time, event.time) &&
                Objects.equals(source, event.source) &&
                Objects.equals(target, event.target) &&
                Objects.equals(type, event.type) &&
                Objects.equals(token, event.token) &&
                Objects.equals(data, event.data) &&
                Objects.equals(message, event.message) &&
                Objects.equals(error, event.error) &&
                Objects.equals(actions, event.actions) &&
                Objects.equals(subprotocols, event.subprotocols);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, refId, channel, time, source, target, type, token, data, message, error, actions, subprotocols);
    }

    @Override
    public String toString() {
        return "Event{" +
                "id='" + id + '\'' +
                ", refId='" + refId + '\'' +
                ", channel='" + channel + '\'' +
                ", time=" + time +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", type='" + type + '\'' +
                ", token='" + token + '\'' +
                ", message='" + message + '\'' +
                ", error='" + error + '\'' +
                ", actions=" + actions +
                '}';
    }

    public static class Builder {
        private final Event event = new Event();

        public Builder id(String id) {
            event.setId(id);
            return this;
        }

        public Builder refId(String refId) {
            event.setRefId(refId);
            return this;
        }

        public Builder channel(String channel) {
            event.setChannel(channel);
            return this;
        }

        public Builder time(Instant time) {
            event.setTime(time);
            return this;
        }

        public Builder source(String source) {
            event.setSource(source);
            return this;
        }

        public Builder target(String target) {
            event.setTarget(target);
            return this;
        }

        public Builder type(String type) {
            event.setType(type);
            return this;
        }

        public Builder type(EventType type) {
            event.setType(type);
            return this;
        }

        public Builder token(String token) {
            event.setToken(token);
            return this;
        }

        public Builder data(Object data) {
            event.setData(data);
            return this;
        }

        public Builder message(String message) {
            event.setMessage(message);
            return this;
        }

        public Builder error(String error) {
            event.setError(error);
            return this;
        }

        public Builder actions(List<String> actions) {
            event.setActions(actions != null ? new ArrayList<>(actions) : null);
            return this;
        }

        public Builder addAction(String action) {
            event.addAction(action);
            return this;
        }

        public Builder subprotocols(List<String> subprotocols) {
            event.setSubprotocols(subprotocols != null ? new ArrayList<>(subprotocols) : null);
            return this;
        }

        public Event build() {
            return event;
        }
    }
}

package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base event message envelope transferred between clients and the WSBroker.
 * Encapsulates transport metadata, routing headers, correlation IDs, and lifecycle data.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseEvent {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
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

    @JsonProperty("refId")
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

    @JsonProperty("session")
    private String session;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error")
    private String error;

    @JsonProperty("actions")
    private List<String> actions;

    @JsonProperty("subprotocols")
    private List<String> subprotocols;

    public BaseEvent() {
        this.id = generateEventId();
    }

    public BaseEvent(String type, String source, String target) {
        this.id = generateEventId();
        this.time = Instant.now();
        this.type = type;
        this.source = source;
        this.target = target;
    }

    public String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setId(String id) {
        this.id = id;
        return (E) this;
    }

    public String getRefId() {
        return refId;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setRefId(String refId) {
        this.refId = refId;
        return (E) this;
    }

    public String getChannel() {
        return channel;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setChannel(String channel) {
        this.channel = channel;
        return (E) this;
    }

    public Instant getTime() {
        return time;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setTime(Instant time) {
        this.time = time;
        return (E) this;
    }

    public String getSource() {
        return source;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setSource(String source) {
        this.source = source;
        return (E) this;
    }

    public String getTarget() {
        return target;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setTarget(String target) {
        this.target = target;
        return (E) this;
    }

    public String getType() {
        return type;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setType(String type) {
        this.type = type;
        return (E) this;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setType(EventType eventType) {
        this.type = eventType != null ? eventType.getValue() : null;
        return (E) this;
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

    public String getSession() {
        return session;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setSession(String session) {
        this.session = session;
        return (E) this;
    }

    public String getMessage() {
        return message;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setMessage(String message) {
        this.message = message;
        return (E) this;
    }

    public String getError() {
        return error;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setError(String error) {
        this.error = error;
        return (E) this;
    }

    public List<String> getActions() {
        return actions;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setActions(List<String> actions) {
        this.actions = actions;
        return (E) this;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E addAction(String action) {
        if (this.actions == null) {
            this.actions = new ArrayList<>();
        }
        this.actions.add(action);
        return (E) this;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEvent> E setSubprotocols(List<String> subprotocols) {
        this.subprotocols = subprotocols;
        return (E) this;
    }

    /**
     * Creates a reply Event correlated with this request Event.
     * Sets {@code id} to a newly generated unique identifier, {@code refId} to this event's {@code id},
     * {@code target} to this event's {@code source} (or channel/target as fallback), and copies over {@code channel} and {@code session}.
     *
     * @param replyType event type of the reply
     * @param payload   data payload for the reply
     * @param <R>       payload type
     * @return reply Event instance
     */
    public <R> Event<R> createReply(EventType replyType, R payload) {
        return createReply(replyType != null ? replyType.getValue() : null, payload);
    }

    /**
     * Creates a reply Event correlated with this request Event.
     * Sets {@code id} to a newly generated unique identifier, {@code refId} to this event's {@code id},
     * {@code target} to this event's {@code source} (or channel/target as fallback), and copies over {@code channel} and {@code session}.
     *
     * @param replyType string representation of event type
     * @param payload   data payload for the reply
     * @param <R>       payload type
     * @return reply Event instance
     */
    public <R> Event<R> createReply(String replyType, R payload) {
        String replyTarget = this.source;
        if (replyTarget == null || replyTarget.isBlank()) {
            replyTarget = this.channel;
        }
        if (replyTarget == null || replyTarget.isBlank()) {
            replyTarget = this.target;
        }
        return Event.<R>builder()
                .type(replyType)
                .target(replyTarget)
                .channel(this.channel)
                .session(this.session)
                .refId(this.id)
                .data(payload)
                .build();
    }

    /**
     * Serializes this BaseEvent instance to JSON string.
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Event to JSON", e);
        }
    }

    /**
     * Deserializes a BaseEvent from JSON string.
     */
    public static BaseEvent fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, BaseEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize BaseEvent from JSON: " + json, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEvent that)) return false;
        return Objects.equals(id, that.id) &&
                Objects.equals(refId, that.refId) &&
                Objects.equals(channel, that.channel) &&
                Objects.equals(time, that.time) &&
                Objects.equals(source, that.source) &&
                Objects.equals(target, that.target) &&
                Objects.equals(type, that.type) &&
                Objects.equals(session, that.session) &&
                Objects.equals(message, that.message) &&
                Objects.equals(error, that.error) &&
                Objects.equals(actions, that.actions) &&
                Objects.equals(subprotocols, that.subprotocols);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, refId, channel, time, source, target, type, session, message, error, actions, subprotocols);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + id + '\'' +
                ", refId='" + refId + '\'' +
                ", channel='" + channel + '\'' +
                ", time=" + time +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", type='" + type + '\'' +
                ", session='" + session + '\'' +
                ", message='" + message + '\'' +
                ", error='" + error + '\'' +
                ", actions=" + actions +
                '}';
    }
}

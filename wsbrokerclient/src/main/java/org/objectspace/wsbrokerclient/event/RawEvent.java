package org.objectspace.wsbrokerclient.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Specialization of Event for raw, untyped or dynamic JSON payloads (Event&lt;JsonNode&gt;).
 * Analogous to RawEvent (Event[jsontext.Value]) in Go wsbrokerclient.
 */
public class RawEvent extends Event<JsonNode> {

    public RawEvent() {
        super();
    }

    public RawEvent(String type, String source, String target) {
        super(type, source, target);
    }

    public RawEvent(String type, String source, String target, JsonNode data) {
        super(type, source, target, data);
    }

    /**
     * Deserializes a RawEvent from JSON string.
     */
    public static RawEvent fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, RawEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize RawEvent from JSON: " + json, e);
        }
    }
}

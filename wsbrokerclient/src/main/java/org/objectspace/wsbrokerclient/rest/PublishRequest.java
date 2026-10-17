package org.objectspace.wsbrokerclient.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Request payload for publishing events via the WSBroker REST API.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PublishRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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

    public PublishRequest() {
    }

    public PublishRequest(String source, String target, String type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public PublishRequest setSource(String source) {
        this.source = source;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public PublishRequest setTarget(String target) {
        this.target = target;
        return this;
    }

    public String getType() {
        return type;
    }

    public PublishRequest setType(String type) {
        this.type = type;
        return this;
    }

    public String getToken() {
        return token;
    }

    public PublishRequest setToken(String token) {
        this.token = token;
        return this;
    }

    public JsonNode getData() {
        return data;
    }

    @JsonProperty("data")
    public PublishRequest setData(JsonNode data) {
        this.data = data;
        return this;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public PublishRequest setData(Object object) {
        if (object == null) {
            this.data = null;
        } else if (object instanceof JsonNode node) {
            this.data = node;
        } else {
            this.data = OBJECT_MAPPER.valueToTree(object);
        }
        return this;
    }

    public String getMessage() {
        return message;
    }

    public PublishRequest setMessage(String message) {
        this.message = message;
        return this;
    }
}

package org.objectspace.wsbrokerclient.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned after an event is published via the WSBroker REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("refId")
    private String refId;

    @JsonProperty("target")
    private String target;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("type")
    private String type;

    @JsonProperty("sent")
    private boolean sent;

    @JsonProperty("subscribers")
    private int subscribers;

    public PublishResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(int subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    public String toString() {
        return "PublishResponse{" +
                "id='" + id + '\'' +
                ", refId='" + refId + '\'' +
                ", target='" + target + '\'' +
                ", channel='" + channel + '\'' +
                ", type='" + type + '\'' +
                ", sent=" + sent +
                ", subscribers=" + subscribers +
                '}';
    }
}

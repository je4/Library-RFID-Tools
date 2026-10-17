package org.objectspace.wsbrokerclient.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned after an event is published via the WSBroker REST API.
 */
public class PublishResponse {

    @JsonProperty("target")
    private String target;

    @JsonProperty("type")
    private String type;

    @JsonProperty("sent")
    private boolean sent;

    @JsonProperty("subscribers")
    private int subscribers;

    public PublishResponse() {
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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
                "target='" + target + '\'' +
                ", type='" + type + '\'' +
                ", sent=" + sent +
                ", subscribers=" + subscribers +
                '}';
    }
}

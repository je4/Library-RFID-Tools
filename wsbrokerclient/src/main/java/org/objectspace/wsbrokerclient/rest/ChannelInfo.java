package org.objectspace.wsbrokerclient.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a WSBroker channel, subscriber count, and connected clients.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelInfo {

    @JsonProperty("name")
    private String name;

    @JsonProperty("subscribers")
    private int subscribers;

    @JsonProperty("clients")
    private List<String> clients = new ArrayList<>();

    public ChannelInfo() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(int subscribers) {
        this.subscribers = subscribers;
    }

    public List<String> getClients() {
        return clients;
    }

    public void setClients(List<String> clients) {
        this.clients = clients != null ? clients : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "ChannelInfo{" +
                "name='" + name + '\'' +
                ", subscribers=" + subscribers +
                ", clients=" + clients +
                '}';
    }
}

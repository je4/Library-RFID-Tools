package org.objectspace.rfid.headless;

import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reusable mock WSBrokerClient for testing that records sent events without network I/O.
 */
public class TestWsBrokerClient extends WSBrokerClient {

    private final List<Event> sentEvents = new CopyOnWriteArrayList<>();
    private volatile boolean connected = true;

    public TestWsBrokerClient(String clientName) {
        super(createConfig(clientName));
    }

    private static WSClientConfig createConfig(String clientName) {
        WSClientConfig config = new WSClientConfig();
        config.setClientName(clientName != null ? clientName : "test-client");
        config.setEndpoint("https://localhost:8765");
        return config;
    }

    @Override
    public CompletableFuture<Void> send(Event event) {
        sentEvents.add(event);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public List<Event> getSentEvents() {
        return sentEvents;
    }

    public void clearSentEvents() {
        sentEvents.clear();
    }
}

package org.objectspace.wsbrokerclient.demo.wschat;

import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.wsbrokerclient.client.ConnectionState;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.ntp.NTPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Controller connecting {@link WSBrokerClient} with {@link MessageStore} and UI components.
 * Manages connection lifecycle, dynamic mTLS, publish/subscribe, channel attachments, and inbound event routing.
 */
public class WSChatClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WSChatClient.class);

    private final WSChatConfig config;
    private final MessageStore store;
    private final WSBrokerClient client;
    private final CertLoader certLoader;
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();

    public WSChatClient(WSChatConfig config, MessageStore store) {
        this.config = config != null ? config : new WSChatConfig();
        this.store = store != null ? store : new MessageStore(this.config.getName(), this.config.getChannels(), 500);

        // 1. Configure mTLS via CertManager if specified
        CertLoader loader = null;
        if (this.config.getClientTls() != null) {
            try {
                loader = CertManager.createClientLoader(this.config.getClientTls());
                logMessage("mTLS Certificate Loader initialized (" + this.config.getClientTls().getType() + ")");
            } catch (Exception e) {
                log.error("Failed to initialize CertLoader: {}", e.getMessage(), e);
                logMessage("Warning: Failed to initialize CertLoader: " + e.getMessage());
            }
        }
        this.certLoader = loader;

        // 2. Configure WSBrokerClient
        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setEndpoint(this.config.getWsbroker());
        clientConfig.setClientName(this.config.getName());
        clientConfig.setChannels(this.config.getChannels());
        clientConfig.setAutoReconnect(true);
        clientConfig.setReconnectInitialDelay(Duration.ofSeconds(1));
        clientConfig.setReconnectMaxDelay(Duration.ofSeconds(10));
        clientConfig.setPingInterval(Duration.ofSeconds(15));
        clientConfig.setCertLoader(this.certLoader);

        this.client = new WSBrokerClient(clientConfig);

        // 3. Register lifecycle hooks
        setupLifecycleListeners();
    }

    private void setupLifecycleListeners() {
        client.addConnectionListener((oldState, newState) -> {
            log.info("WSBroker connection state: {} -> {}", oldState, newState);
            switch (newState) {
                case CONNECTED -> {
                    store.setStatus("Connected");
                    logMessage("Connected to WSBroker (" + config.getWsbroker() + ")");
                }
                case CONNECTING -> {
                    store.setStatus("Connecting");
                    logMessage("Connecting to WSBroker...");
                }
                case RECONNECTING -> {
                    store.setStatus("Reconnecting");
                    logMessage("Reconnecting to WSBroker...");
                }
                case DISCONNECTED -> {
                    store.setStatus("Disconnected");
                    logMessage("Disconnected from WSBroker");
                }
                case CLOSED -> {
                    store.setStatus("Closed");
                    logMessage("WSBroker client connection closed");
                }
            }
        });

        client.onEvent(this::handleIncomingEvent);
    }

    /**
     * Starts the client connection asynchronously in background.
     */
    public CompletableFuture<Void> start() {
        store.setStatus("Connecting");
        logMessage("Initiating connection to " + config.getWsbroker() + " as client '" + config.getName() + "'...");
        return client.connect().exceptionally(ex -> {
            log.error("Failed to connect initially: {}", ex.getMessage());
            store.setStatus("Disconnected");
            logMessage("Initial connection failed: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Sends a chat message to the target channel.
     * Optimistically appends the message to the local store for instant UI feedback.
     */
    public CompletableFuture<Void> sendMessage(String channel, String content) {
        if (channel == null || channel.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Channel cannot be empty"));
        }
        if (content == null || content.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message content cannot be empty"));
        }

        String targetChannel = channel.trim();
        String messageText = content.trim();

        // Send over WebSocket broker
        CompletableFuture<Void> sendFuture = client.publish(targetChannel, messageText);

        // Optimistically record in local store for the sender
        String msgId = System.currentTimeMillis() + "-" + config.getName();
        ChatMessage localMsg = new ChatMessage(msgId, config.getName(), targetChannel, messageText, Instant.now(), true);
        store.addMessage(localMsg);
        logMessage("Sent message to #" + targetChannel + ": " + messageText);

        return sendFuture;
    }

    /**
     * Dynamically attaches and subscribes to a new channel.
     */
    public CompletableFuture<Void> attachChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String ch = channel.trim();
        store.addChannel(ch);
        client.subscribe(ch);
        logMessage("Subscribed to channel #" + ch);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Detaches / unsubscribes from a channel on the broker.
     */
    public CompletableFuture<Void> detachChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String ch = channel.trim();
        client.unsubscribe(ch);
        logMessage("Unsubscribed from channel #" + ch);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Queries broker for NTP time synchronization and roundtrip latency.
     */
    public CompletableFuture<NTPResponse> queryNtp() {
        logMessage("Querying NTP time synchronization...");
        return client.queryNTP(Duration.ofSeconds(5)).thenApply(resp -> {
            logMessage(String.format("NTP Sync: Offset=%.2fms, RTT=%.2fms, Stratum=%d",
                    resp.getOffsetMillis(), resp.getRoundTripDelayMillis(), resp.getStratum()));
            return resp;
        });
    }

    /**
     * Handles incoming events from the broker.
     */
    private void handleIncomingEvent(Event evt) {
        if (evt == null) return;

        // Skip self messages to prevent duplicate echoes since sendMessage already stored them
        if (config.getName().equalsIgnoreCase(evt.getSource())) {
            return;
        }

        String channel = evt.getTarget();
        if (channel == null || channel.isBlank()) {
            channel = evt.getSource();
        }
        if (channel == null || channel.isBlank()) {
            channel = store.getActiveChannel();
        }

        String content = null;
        if (evt.getMessage() != null && !evt.getMessage().isBlank()) {
            content = evt.getMessage();
        } else if (evt.getData() != null) {
            if (evt.getData().isTextual()) {
                content = evt.getData().asText();
            } else {
                content = evt.getData().toString();
            }
        } else {
            content = "";
        }

        String sender = evt.getSource() != null && !evt.getSource().isBlank() ? evt.getSource() : "Unknown";
        String msgId = System.currentTimeMillis() + "-" + sender;

        ChatMessage chatMsg = new ChatMessage(msgId, sender, channel, content, evt.getTime() != null ? evt.getTime() : Instant.now(), false);
        store.addMessage(chatMsg);
        logMessage("Received [" + evt.getType() + "] from @" + sender + " in #" + channel + ": " + content);
    }

    public void addLogListener(Consumer<String> listener) {
        if (listener != null && !logListeners.contains(listener)) {
            logListeners.add(listener);
        }
    }

    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    private void logMessage(String msg) {
        String timestamped = String.format("[%tT] %s", System.currentTimeMillis(), msg);
        for (Consumer<String> l : logListeners) {
            try {
                l.accept(timestamped);
            } catch (Exception ignored) {}
        }
    }

    public WSChatConfig getConfig() {
        return config;
    }

    public MessageStore getStore() {
        return store;
    }

    public WSBrokerClient getClient() {
        return client;
    }

    public CertLoader getCertLoader() {
        return certLoader;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing WSBrokerClient: {}", e.getMessage());
        }
        if (certLoader != null) {
            try {
                certLoader.close();
            } catch (Exception e) {
                log.warn("Error closing CertLoader: {}", e.getMessage());
            }
        }
    }
}

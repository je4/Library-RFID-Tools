package org.objectspace.wsbrokerclient.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.BaseEvent;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventHandler;
import org.objectspace.wsbrokerclient.event.EventType;
import org.objectspace.wsbrokerclient.event.TypedEventHandler;
import org.objectspace.wsbrokerclient.ntp.NTPPacket;
import org.objectspace.wsbrokerclient.ntp.NTPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Robust, high-performance WebSocket client for WSBroker with dynamic mTLS certificate hot-reloading.
 */
public class WSBrokerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WSBrokerClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final WSClientConfig config;
    private final String clientName;
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final HttpClient httpClient;
    private final CertLoader ownedCertLoader;
    private final Consumer<KeyCertPair> certReloadListener;

    private final AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerExecutor;

    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<EventHandler> globalHandlers = new CopyOnWriteArrayList<>();
    private final Map<String, List<EventHandler>> typeHandlers = new ConcurrentHashMap<>();
    private final Map<String, List<EventHandler>> channelHandlers = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<CompletableFuture<NTPResponse>> pendingNtpQueries = new ConcurrentLinkedQueue<>();

    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> reconnectTask;
    private long currentReconnectDelayMs;

    public WSBrokerClient(WSClientConfig config) {
        this.config = Objects.requireNonNull(config, "WSClientConfig cannot be null");
        this.clientName = config.getClientName() != null && !config.getClientName().isBlank()
                ? config.getClientName().trim()
                : "java-client-" + UUID.randomUUID().toString().substring(0, 8);

        if (config.getChannels() != null) {
            this.subscribedChannels.addAll(config.getChannels());
        }

        // Configure TLS & certmanager
        CertLoader loaderToClose = null;
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout());

        if (config.getCertLoader() != null) {
            CertLoader l = config.getCertLoader();
            clientBuilder.sslContext(l.getSSLContext());
            this.certReloadListener = pair -> log.info("Client mTLS certificate reloaded for subject: {}", pair.getSubjectDN());
            l.addListener(this.certReloadListener);
        } else if (config.getCertManagerConfig() != null) {
            CertLoader l = CertManager.createClientLoader(config.getCertManagerConfig());
            loaderToClose = l;
            clientBuilder.sslContext(l.getSSLContext());
            this.certReloadListener = pair -> log.info("Client mTLS certificate reloaded for subject: {}", pair.getSubjectDN());
            l.addListener(this.certReloadListener);
        } else if (config.getSslContext() != null) {
            clientBuilder.sslContext(config.getSslContext());
            this.certReloadListener = null;
        } else {
            this.certReloadListener = null;
        }

        this.httpClient = clientBuilder.build();
        this.ownedCertLoader = loaderToClose;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wsbroker-scheduler-" + clientName);
            t.setDaemon(true);
            return t;
        });

        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "wsbroker-worker-" + clientName);
            t.setDaemon(true);
            return t;
        });

        this.currentReconnectDelayMs = config.getReconnectInitialDelay().toMillis();
    }

    /**
     * Factory method creating a new WSBrokerClient.
     */
    public static WSBrokerClient create(WSClientConfig config) {
        return new WSBrokerClient(config);
    }

    /**
     * Gets the current client connection state.
     */
    public ConnectionState getState() {
        return state.get();
    }

    /**
     * Checks if the WebSocket client is currently connected.
     */
    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    /**
     * Gets the configured or generated client name.
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Gets the set of subscribed channel names.
     */
    public Set<String> getSubscribedChannels() {
        return Collections.unmodifiableSet(new HashSet<>(subscribedChannels));
    }

    /**
     * Registers a connection state listener.
     */
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    /**
     * Unregisters a connection state listener.
     */
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * Initiates asynchronous connection to the WSBroker.
     *
     * @return CompletableFuture completing when connected or failed
     */
    public CompletableFuture<Void> connect() {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
        }
        if (state.get() == ConnectionState.CONNECTED) {
            return CompletableFuture.completedFuture(null);
        }

        setState(ConnectionState.CONNECTING);
        CompletableFuture<Void> future = new CompletableFuture<>();

        doConnect(future);
        return future;
    }

    /**
     * Connects synchronously, waiting up to the specified timeout duration.
     *
     * @param timeout maximum wait time
     * @throws TimeoutException     if timeout expires
     * @throws ExecutionException   if connection fails
     * @throws InterruptedException if thread is interrupted
     */
    public void connectSync(Duration timeout) throws TimeoutException, ExecutionException, InterruptedException {
        connect().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void doConnect(CompletableFuture<Void> initialFuture) {
        if (closed.get()) {
            if (initialFuture != null) {
                initialFuture.completeExceptionally(new IllegalStateException("Client is closed"));
            }
            return;
        }

        String wsUrl = buildWsUrl();
        log.debug("Connecting WebSocket to URL: {}", wsUrl);

        WebSocket.Builder wsBuilder = httpClient.newWebSocketBuilder()
                .connectTimeout(config.getConnectTimeout());

        if (config.getSubprotocols() != null && !config.getSubprotocols().isEmpty()) {
            if (config.getSubprotocols().size() == 1) {
                wsBuilder.subprotocols(config.getSubprotocols().get(0));
            } else {
                wsBuilder.subprotocols(config.getSubprotocols().get(0), config.getSubprotocols().subList(1, config.getSubprotocols().size()).toArray(new String[0]));
            }
        }

        if (config.getBearer() != null && !config.getBearer().isBlank()) {
            wsBuilder.header("Authorization", "Bearer " + config.getBearer());
        }

        wsBuilder.buildAsync(URI.create(wsUrl), new WSListener())
                .whenComplete((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn("WebSocket connection attempt failed to {}: {}", wsUrl, throwable.getMessage());
                        setState(ConnectionState.DISCONNECTED);

                        if (initialFuture != null && !initialFuture.isDone()) {
                            if (config.isAutoReconnect()) {
                                initialFuture.complete(null);
                            } else {
                                initialFuture.completeExceptionally(throwable);
                            }
                        }

                        if (config.isAutoReconnect() && !closed.get()) {
                            scheduleReconnect();
                        }
                    } else {
                        log.info("WebSocket connected successfully to {} as client '{}'", wsUrl, clientName);
                        activeWebSocket.set(ws);
                        currentReconnectDelayMs = config.getReconnectInitialDelay().toMillis();
                        setState(ConnectionState.CONNECTED);

                        startPingLoop();

                        if (initialFuture != null && !initialFuture.isDone()) {
                            initialFuture.complete(null);
                        }
                    }
                });
    }

    private String buildWsUrl() {
        String endpoint = config.getEndpoint() != null && !config.getEndpoint().isBlank()
                ? config.getEndpoint().trim()
                : "ws://localhost:8080/ws";

        if (endpoint.startsWith("http://")) {
            endpoint = "ws://" + endpoint.substring(7);
        } else if (endpoint.startsWith("https://")) {
            endpoint = "wss://" + endpoint.substring(8);
        } else if (!endpoint.startsWith("ws://") && !endpoint.startsWith("wss://")) {
            endpoint = "ws://" + endpoint;
        }

        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        StringBuilder urlBuilder = new StringBuilder(endpoint);
        if (!endpoint.contains("/ws")) {
            urlBuilder.append("/ws");
        }

        // Append clientName and mode if path ends at /ws
        if (urlBuilder.toString().endsWith("/ws")) {
            urlBuilder.append("/").append(urlEncode(clientName)).append("/rw");
        }

        // Query parameters for channels and bearer token
        List<String> queryParams = new ArrayList<>();
        for (String ch : subscribedChannels) {
            queryParams.add("channel=" + urlEncode(ch));
        }
        if (config.getBearer() != null && !config.getBearer().isBlank()) {
            queryParams.add("token=" + urlEncode(config.getBearer()));
        }

        if (!queryParams.isEmpty()) {
            urlBuilder.append("?").append(String.join("&", queryParams));
        }

        return urlBuilder.toString();
    }

    private void scheduleReconnect() {
        if (closed.get()) {
            return;
        }
        setState(ConnectionState.RECONNECTING);
        long delay = currentReconnectDelayMs;
        log.info("Scheduling WebSocket reconnect in {} ms...", delay);

        reconnectTask = scheduler.schedule(() -> {
            if (!closed.get()) {
                doConnect(null);
            }
        }, delay, TimeUnit.MILLISECONDS);

        // Exponential backoff calculation
        currentReconnectDelayMs = Math.min(
                config.getReconnectMaxDelay().toMillis(),
                (long) (currentReconnectDelayMs * config.getReconnectMultiplier())
        );
    }

    private void startPingLoop() {
        stopPingLoop();
        if (config.getPingInterval().isZero() || config.getPingInterval().isNegative()) {
            return;
        }

        long intervalMs = config.getPingInterval().toMillis();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                sendPing().exceptionally(err -> {
                    log.debug("Heartbeat ping send failed: {}", err.getMessage());
                    return null;
                });
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopPingLoop() {
        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }
    }

    private void setState(ConnectionState newState) {
        ConnectionState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            log.debug("Connection state changed: {} -> {}", oldState, newState);
            for (ConnectionListener listener : connectionListeners) {
                try {
                    listener.onStateChanged(oldState, newState);
                } catch (Exception e) {
                    log.warn("Error in ConnectionListener: {}", e.getMessage(), e);
                }
            }
        }
    }

    // --- Subscription Management ---

    /**
     * Subscribes to a channel topic.
     *
     * @param channel channel name
     */
    public void subscribe(String channel) {
        if (channel != null && !channel.isBlank()) {
            String cleanChannel = channel.trim();
            subscribedChannels.add(cleanChannel);
            if (isConnected()) {
                send(Event.createAttach(clientName, cleanChannel)).exceptionally(err -> {
                    log.debug("Failed to send attach event: {}", err.getMessage());
                    return null;
                });
            }
        }
    }

    /**
     * Unsubscribes from a channel topic.
     *
     * @param channel channel name
     */
    public void unsubscribe(String channel) {
        if (channel != null) {
            String cleanChannel = channel.trim();
            subscribedChannels.remove(cleanChannel);
            channelHandlers.remove(cleanChannel);
            if (isConnected()) {
                send(Event.createDetach(clientName, cleanChannel)).exceptionally(err -> {
                    log.debug("Failed to send detach event: {}", err.getMessage());
                    return null;
                });
            }
        }
    }

    // --- Sending Messages ---

    /**
     * Sends an Event envelope over the WebSocket connection.
     *
     * @param event the event to send
     * @return CompletableFuture completing when message is transmitted
     */
    public CompletableFuture<Void> send(BaseEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");
        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(BaseEvent.generateEventId());
        }
        if (event.getSource() == null || event.getSource().isBlank()) {
            event.setSource(clientName);
        }
        if (event.getTime() == null) {
            event.setTime(Instant.now());
        }

        WebSocket ws = activeWebSocket.get();
        if (ws == null || !isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not connected"));
        }

        String json = event.toJson();
        return ws.sendText(json, true).thenApply(r -> null);
    }

    /**
     * Sends an Event envelope over the WebSocket connection (convenience overload).
     *
     * @param event the event to send
     * @return CompletableFuture completing when message is transmitted
     */
    public CompletableFuture<Void> send(Event event) {
        return send((BaseEvent) event);
    }

    /**
     * Publishes a message payload to a target destination or channel.
     *
     * @param target  target client or channel
     * @param payload data payload
     * @return CompletableFuture
     */
    public CompletableFuture<Void> publish(String target, Object payload) {
        return send(Event.createMessage(clientName, target, payload));
    }

    /**
     * Sends a message payload to a target destination or channel (alias for publish).
     *
     * @param target  target client or channel
     * @param payload data payload
     * @return CompletableFuture
     */
    public CompletableFuture<Void> sendMessage(String target, Object payload) {
        return publish(target, payload);
    }

    /**
     * Publishes a custom-typed event to a target destination.
     *
     * @param target  target client or channel
     * @param type    custom event type
     * @param payload data payload
     * @return CompletableFuture
     */
    public CompletableFuture<Void> publish(String target, String type, Object payload) {
        Event event = Event.builder()
                .source(clientName)
                .target(target)
                .type(type)
                .data(payload)
                .build();
        return send(event);
    }

    /**
     * Dispatches a remote action invocation to a target.
     *
     * @param target  target client or channel
     * @param action  action identifier
     * @param payload action payload
     * @return CompletableFuture
     */
    public CompletableFuture<Void> sendAction(String target, String action, Object payload) {
        return send(Event.createAction(clientName, target, action, payload));
    }

    /**
     * Sends a device metadata query command message (type = "message") to the specified target reader client or channel.
     *
     * @param target the target reader client name or channel
     * @return CompletableFuture completing when the query message is sent
     */
    public CompletableFuture<Void> queryDeviceMetadata(String target) {
        Map<String, Object> command = new HashMap<>();
        command.put("event", "rfid:get_device_metadata");
        Event event = Event.createMessage(clientName, target, command);
        return send(event);
    }

    /**
     * Broadcasts a message payload to all connected clients.
     *
     * @param payload data payload
     * @return CompletableFuture
     */
    public CompletableFuture<Void> broadcast(Object payload) {
        return send(Event.createBroadcast(clientName, payload));
    }

    /**
     * Sends a heartbeat ping event.
     *
     * @return CompletableFuture
     */
    public CompletableFuture<Void> sendPing() {
        return send(Event.createPing(clientName));
    }

    /**
     * Sends a heartbeat pong reply event.
     *
     * @param target destination client or broker
     * @return CompletableFuture
     */
    public CompletableFuture<Void> sendPong(String target) {
        return sendPong(target, null);
    }

    /**
     * Sends a heartbeat pong reply event with refId correlation.
     *
     * @param target destination client or broker
     * @param refId  correlated request event ID
     * @return CompletableFuture
     */
    public CompletableFuture<Void> sendPong(String target, String refId) {
        return send(Event.createPong(clientName, target != null ? target : "wsbroker", refId));
    }

    // --- NTP Time Synchronization over WebSocket ---

    /**
     * Queries the WSBroker for NTP time synchronization.
     *
     * @param timeout maximum wait duration
     * @return CompletableFuture yielding the calculated NTPResponse
     */
    public CompletableFuture<NTPResponse> queryNTP(Duration timeout) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not connected"));
        }

        NTPPacket queryPacket = NTPPacket.createClientQuery();
        byte[] encoded = queryPacket.encode();

        Event queryEvent = Event.createNtpQuery(clientName, encoded);

        CompletableFuture<NTPResponse> ntpFuture = new CompletableFuture<>();
        pendingNtpQueries.add(ntpFuture);

        Duration effectiveTimeout = timeout != null ? timeout : Duration.ofSeconds(5);
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (pendingNtpQueries.remove(ntpFuture) && !ntpFuture.isDone()) {
                ntpFuture.completeExceptionally(new TimeoutException("NTP query timed out after " + effectiveTimeout));
            }
        }, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

        ntpFuture.whenComplete((res, ex) -> timeoutTask.cancel(false));

        send(queryEvent).exceptionally(err -> {
            pendingNtpQueries.remove(ntpFuture);
            ntpFuture.completeExceptionally(err);
            return null;
        });

        return ntpFuture;
    }

    // --- Event Handlers & Routing ---

    /**
     * Registers a global handler invoked for every incoming event.
     */
    public void onEvent(EventHandler handler) {
        if (handler != null) {
            globalHandlers.add(handler);
        }
    }

    /**
     * Registers a handler for incoming message events (type = "message").
     */
    public void onMessage(EventHandler handler) {
        onType(EventType.CLIENT_MESSAGE, handler);
    }

    /**
     * Registers a typed handler for incoming message events (type = "message") with automatic JSON payload deserialization.
     */
    public <T> void onMessage(Class<T> clazz, TypedEventHandler<T> handler) {
        onType(EventType.CLIENT_MESSAGE, clazz, handler);
    }

    /**
     * Registers a handler for a specific EventType enum.
     */
    public void onType(EventType type, EventHandler handler) {
        if (type != null && handler != null) {
            onType(type.getValue(), handler);
        }
    }

    /**
     * Registers a handler for a specific event type string.
     */
    public void onType(String type, EventHandler handler) {
        if (type != null && handler != null) {
            typeHandlers.computeIfAbsent(type.toLowerCase(), k -> new CopyOnWriteArrayList<>()).add(handler);
        }
    }

    /**
     * Registers a typed handler for a specific EventType with automatic JSON payload deserialization.
     */
    public <T> void onType(EventType type, Class<T> clazz, TypedEventHandler<T> handler) {
        if (type != null && handler != null && clazz != null) {
            onType(type, evt -> {
                T payload = evt.getDataAs(clazz);
                handler.onEvent(evt, payload);
            });
        }
    }

    /**
     * Registers a handler for events arriving on a specific channel.
     */
    public void onChannel(String channel, EventHandler handler) {
        if (channel != null && handler != null) {
            subscribe(channel);
            channelHandlers.computeIfAbsent(channel.trim(), k -> new CopyOnWriteArrayList<>()).add(handler);
        }
    }

    /**
     * Registers a typed handler for events arriving on a specific channel.
     */
    public <T> void onChannel(String channel, Class<T> clazz, TypedEventHandler<T> handler) {
        if (channel != null && handler != null && clazz != null) {
            onChannel(channel, evt -> {
                T payload = evt.getDataAs(clazz);
                handler.onEvent(evt, payload);
            });
        }
    }

    void handleIncomingEvent(Event<?> evt) {
        log.debug("Received event: type='{}', source='{}', target='{}'", evt.getType(), evt.getSource(), evt.getTarget());

        // Heartbeat auto-reply
        if (evt.isType(EventType.CLIENT_PING)) {
            String target = evt.getSource();
            if (target == null || target.isBlank()) {
                target = "wsbroker";
            }
            sendPong(target, evt.getId()).exceptionally(err -> {
                log.debug("Auto-pong response failed: {}", err.getMessage());
                return null;
            });
            return;
        }

        // Heartbeat pong received
        if (evt.isType(EventType.CLIENT_PONG)) {
            log.debug("Received pong from {}", evt.getSource());
            return;
        }

        // Handle NTP Response or Error
        if (evt.isType(EventType.CLIENT_NTP_RESPONSE) || evt.isType(EventType.CLIENT_NTP_ERROR)) {
            CompletableFuture<NTPResponse> future = pendingNtpQueries.poll();
            if (future != null) {
                Instant destinationTime = Instant.now();
                if (evt.isType(EventType.CLIENT_NTP_ERROR)) {
                    String errorMsg = "Unknown NTP error";
                    if (evt.getError() != null && !evt.getError().isBlank()) {
                        errorMsg = evt.getError();
                    } else if (evt.getDataAsString() != null) {
                        errorMsg = evt.getDataAsString();
                    }
                    future.completeExceptionally(new RuntimeException("NTP broker error: " + errorMsg));
                } else {
                    try {
                        byte[] ntpBytes = extractNtpBytes(evt.getData());
                        NTPPacket packet = NTPPacket.decode(ntpBytes);
                        NTPResponse response = new NTPResponse(packet, destinationTime);
                        future.complete(response);
                    } catch (Exception e) {
                        future.completeExceptionally(new IllegalArgumentException("Failed to decode NTP response packet", e));
                    }
                }
                return;
            }
        }

        // Route to registered listeners asynchronously
        workerExecutor.submit(() -> {
            // Global handlers
            for (EventHandler h : globalHandlers) {
                invokeHandler(h, evt);
            }

            // Type handlers
            if (evt.getType() != null) {
                List<EventHandler> handlers = typeHandlers.get(evt.getType().toLowerCase());
                if (handlers != null) {
                    for (EventHandler h : handlers) {
                        invokeHandler(h, evt);
                    }
                }
            }

            // Channel handlers
            if (evt.getTarget() != null) {
                List<EventHandler> handlers = channelHandlers.get(evt.getTarget());
                if (handlers != null) {
                    for (EventHandler h : handlers) {
                        invokeHandler(h, evt);
                    }
                }
            }
            if (evt.getChannel() != null && !evt.getChannel().equals(evt.getTarget())) {
                List<EventHandler> handlers = channelHandlers.get(evt.getChannel());
                if (handlers != null) {
                    for (EventHandler h : handlers) {
                        invokeHandler(h, evt);
                    }
                }
            }
        });
    }

    private byte[] extractNtpBytes(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("NTP data payload is missing");
        }
        if (data instanceof byte[] bytes) {
            return bytes;
        }
        if (data instanceof String str) {
            return Base64.getDecoder().decode(str);
        }
        if (data instanceof JsonNode node) {
            if (node.isBinary()) {
                try {
                    return node.binaryValue();
                } catch (Exception ignored) {}
            }
            if (node.isTextual()) {
                return Base64.getDecoder().decode(node.asText());
            }
            if (node.isArray()) {
                byte[] bytes = new byte[node.size()];
                for (int i = 0; i < node.size(); i++) {
                    bytes[i] = (byte) node.get(i).asInt();
                }
                return bytes;
            }
            throw new IllegalArgumentException("Unsupported NTP payload format: " + node.getNodeType());
        }
        throw new IllegalArgumentException("Unsupported NTP payload format: " + data.getClass().getName());
    }

    private void invokeHandler(EventHandler handler, Event<?> event) {
        try {
            handler.onEvent(event);
        } catch (Exception e) {
            log.error("Exception during event dispatch: {}", e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closing WSBrokerClient '{}'...", clientName);
            setState(ConnectionState.CLOSING);

            stopPingLoop();
            if (reconnectTask != null) {
                reconnectTask.cancel(true);
            }

            WebSocket ws = activeWebSocket.getAndSet(null);
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing").join();
                } catch (Exception ignored) {
                }
            }

            CompletableFuture<NTPResponse> f;
            while ((f = pendingNtpQueries.poll()) != null) {
                f.completeExceptionally(new IllegalStateException("Client is closing"));
            }

            scheduler.shutdownNow();
            workerExecutor.shutdown();

            if (ownedCertLoader != null) {
                try {
                    ownedCertLoader.close();
                } catch (Exception e) {
                    log.warn("Error closing owned CertLoader: {}", e.getMessage(), e);
                }
            }

            setState(ConnectionState.CLOSED);
            log.info("WSBrokerClient '{}' closed successfully.", clientName);
        }
    }

    // --- WebSocket.Listener Implementation ---

    private class WSListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            log.debug("WebSocket onOpen callback received");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String fullJson = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    Event evt = Event.fromJson(fullJson);
                    handleIncomingEvent(evt);
                } catch (Exception e) {
                    log.warn("Failed to parse incoming WebSocket JSON message: '{}'", fullJson, e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("WebSocket connection closed by remote: status={}, reason='{}'", statusCode, reason);
            activeWebSocket.compareAndSet(webSocket, null);
            stopPingLoop();
            setState(ConnectionState.DISCONNECTED);

            if (config.isAutoReconnect() && !closed.get()) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("WebSocket communication error: {}", error.getMessage());
            activeWebSocket.compareAndSet(webSocket, null);
            stopPingLoop();
            setState(ConnectionState.DISCONNECTED);

            if (config.isAutoReconnect() && !closed.get()) {
                scheduleReconnect();
            }
        }
    }
}

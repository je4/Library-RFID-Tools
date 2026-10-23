package org.objectspace.rfid.headless;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.headless.payload.BaseRfidPayload;
import org.objectspace.rfid.headless.payload.DeviceMetadataPayload;
import org.objectspace.rfid.headless.payload.TagReadPayload;
import org.objectspace.rfid.headless.payload.TagsClearedPayload;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.WSBroker;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventHandler;
import org.objectspace.wsbrokerclient.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages WebSocket broker connection and dispatches RFID tag read events
 * and device metadata to configured WSBroker channels.
 */
public class HeadlessWsBrokerDispatcher implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessWsBrokerDispatcher.class);

	private final String clientName;
	private final String endpoint;
	private final List<String> channels;
	private final CertManagerConfig certConfig;
	private final CertLoader certLoader;
	private final WSBrokerClient client;
	private final boolean active;
	private volatile ISO15693Reader reader;

	/**
	 * Creates a new HeadlessWsBrokerDispatcher from configuration.
	 */
	public static HeadlessWsBrokerDispatcher fromConfiguration(AbstractConfiguration config) {
		if (config == null) {
			return new HeadlessWsBrokerDispatcher(null, null, null, null, false);
		}

		boolean active = HeadlessConfigHelper.isWsBrokerActive(config);
		String clientName = HeadlessConfigHelper.getClientName(config);
		String endpoint = HeadlessConfigHelper.getWsBrokerEndpoint(config);
		List<String> channels = HeadlessConfigHelper.getChannels(config);
		CertManagerConfig tlsConfig = HeadlessConfigHelper.getClientTlsConfig(config);

		return new HeadlessWsBrokerDispatcher(clientName, endpoint, channels, tlsConfig, active);
	}

	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertManagerConfig certConfig) {
		this(clientName, endpoint, channels, certConfig, endpoint != null && !endpoint.isBlank());
	}

	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertManagerConfig certConfig, boolean active) {
		this.clientName = (clientName != null && !clientName.isBlank()) ? clientName.trim() : HeadlessConfigHelper.DEFAULT_CLIENT_NAME;
		this.endpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint.trim() : null;
		this.channels = (channels != null) ? Collections.unmodifiableList(new ArrayList<>(channels)) : Collections.emptyList();
		this.certConfig = certConfig;

		if (active && this.endpoint != null && !this.endpoint.isEmpty()) {
			this.active = true;

			// 1. Initialize mTLS via CertManager if specified
			CertLoader loader = null;
			if (this.certConfig != null) {
				try {
					loader = CertManager.createClientLoader(this.certConfig);
					logger.info("WSBroker mTLS Certificate Loader initialized ({})", this.certConfig.getType());
				} catch (Exception e) {
					logger.error("Failed to initialize CertLoader for WSBroker: {}", e.getMessage(), e);
				}
			}
			this.certLoader = loader;

			// 2. Configure WSBrokerClient
			WSClientConfig clientConfig = new WSClientConfig();
			clientConfig.setEndpoint(this.endpoint);
			clientConfig.setClientName(this.clientName);
			clientConfig.setChannels(this.channels);
			clientConfig.setAutoReconnect(true);
			clientConfig.setReconnectInitialDelay(Duration.ofSeconds(1));
			clientConfig.setReconnectMaxDelay(Duration.ofSeconds(10));
			clientConfig.setPingInterval(Duration.ofSeconds(15));
			clientConfig.setCertLoader(this.certLoader);

			this.client = WSBroker.createClient(clientConfig);
			this.client.addConnectionListener((oldState, newState) -> {
				logger.info("WSBroker connection state: {} -> {}", oldState, newState);
			});
			registerDefaultHandlers();
			logger.info("WSBroker Dispatcher initialized: endpoint={}, clientName={}, channels={}",
					this.endpoint, this.clientName, this.channels);
		} else {
			this.active = false;
			this.certLoader = null;
			this.client = null;
			if (!active) {
				logger.info("WSBroker Dispatcher disabled (active=false).");
			} else {
				logger.info("WSBroker Dispatcher disabled (no <wsbroker> endpoint configured).");
			}
		}
	}

	/**
	 * Constructor for testing with pre-instantiated client.
	 */
	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertLoader certLoader, WSBrokerClient client) {
		this(clientName, endpoint, channels, certLoader, client, endpoint != null && client != null);
	}

	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertLoader certLoader, WSBrokerClient client, boolean active) {
		this.clientName = clientName;
		this.endpoint = endpoint;
		this.channels = (channels != null) ? Collections.unmodifiableList(new ArrayList<>(channels)) : Collections.emptyList();
		this.certConfig = null;
		this.certLoader = certLoader;
		this.client = client;
		this.active = active && (endpoint != null && client != null);
		if (this.active) {
			registerDefaultHandlers();
		}
	}

	/**
	 * Initiates asynchronous connection to the WSBroker server.
	 */
	public CompletableFuture<Void> connect() {
		if (!active || client == null) {
			return CompletableFuture.completedFuture(null);
		}
		logger.info("Connecting to WSBroker at '{}' as client '{}'...", endpoint, clientName);
		return client.connect().exceptionally(ex -> {
			logger.warn("Initial connection attempt to WSBroker failed (auto-reconnect active): {}", ex.getMessage());
			return null;
		});
	}

	/**
	 * Dispatches an RFID event payload to all configured WSBroker channels.
	 */
	public void dispatch(BaseRfidPayload payload) {
		if (!active || client == null || channels.isEmpty() || payload == null) {
			return;
		}

		String summaryMsg = payload.getSummaryMessage();

		for (String channel : channels) {
			try {
				Event event = Event.createMessage(clientName, channel, payload);
				if (summaryMsg != null && !summaryMsg.isBlank()) {
					event.setMessage(summaryMsg);
				}
				client.send(event).exceptionally(ex -> {
					logger.debug("Failed to dispatch to channel #{}: {}", channel, ex.getMessage());
					return null;
				});
				logger.debug("[WSBroker] Dispatched {} to channel #{}", payload.getEvent(), channel);
			} catch (Exception e) {
				logger.warn("[WSBroker] Failed to send {} to channel #{}: {}", payload.getEvent(), channel, e.getMessage());
			}
		}
	}

	/**
	 * Dispatches a scanned RFID tag payload to all configured WSBroker channels.
	 */
	public void dispatchTag(TagReadPayload payload) {
		dispatch(payload);
	}

	/**
	 * Dispatches a scanned RFID tag to all configured WSBroker channels.
	 */
	public void dispatchTag(String uid, String itemId, String rawHex, String isil, String country,
							boolean crcOk, String tagName, String sessionName, long timestamp,
							String clientId, FinnishDataModel metadata) {
		dispatch(new TagReadPayload(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, metadata));
	}

	/**
	 * Dispatches a reader cleared payload to all configured WSBroker channels.
	 */
	public void dispatchEmpty(TagsClearedPayload payload) {
		dispatch(payload);
	}

	/**
	 * Dispatches a reader cleared / all tags removed event to all configured WSBroker channels.
	 */
	public void dispatchEmpty(String sessionName, long timestamp, String clientId) {
		dispatch(new TagsClearedPayload(sessionName, timestamp, clientId));
	}

	/**
	 * Registers default message, action and event handlers for device metadata queries.
	 */
	private void registerDefaultHandlers() {
		if (client == null) {
			return;
		}

		EventHandler metadataHandler = this::handleMetadataQuery;

		// 1. Primary handler: onMessage compatible with wsbrokerdisplay Controller pattern
		client.onType(EventType.CLIENT_MESSAGE, this::handleIncomingMessage);

		// 2. Action handlers for backwards compatibility with type="action"
		client.onAction("get_device_metadata", metadataHandler);
		client.onAction("get_device_info", metadataHandler);
		client.onAction("device_metadata", metadataHandler);
		client.onAction("device_info", metadataHandler);
		client.onAction("get_metadata", metadataHandler);
		client.onAction("query_device", metadataHandler);
		client.onAction("info", metadataHandler);

		// 3. General event listener fallback for non-message targeted queries
		client.onEvent(this::handleGeneralEvent);
	}

	/**
	 * Handles incoming WebSocket message events (type="message") following the Go Controller HandleEvent pattern.
	 *
	 * @param evt incoming message event
	 */
	public void handleIncomingMessage(Event evt) {
		if (evt == null) {
			return;
		}

		// Ignore events emitted by ourselves to prevent loops
		if (clientName != null && clientName.equalsIgnoreCase(evt.getSource())) {
			return;
		}

		// Extract command / event name from payload data
		String eventName = null;
		JsonNode dataNode = evt.getData();
		if (dataNode != null && dataNode.isObject()) {
			if (dataNode.hasNonNull("event")) {
				eventName = dataNode.get("event").asText();
			}
		} else if (dataNode != null && dataNode.isTextual()) {
			eventName = dataNode.asText();
		}

		if ((eventName == null || eventName.isBlank()) && evt.getActions() != null && !evt.getActions().isEmpty()) {
			eventName = evt.getActions().get(0);
		}
		if ((eventName == null || eventName.isBlank()) && evt.getMessage() != null) {
			eventName = evt.getMessage();
		}

		// Ignore empty actions or response messages sent over channels (prevent echo loops)
		if (eventName == null || eventName.isBlank() || eventName.toLowerCase().endsWith("_response")) {
			return;
		}

		// Check if message is addressed to this reader or to a shared channel
		boolean isAddressedToUs = (evt.getTarget() == null || evt.getTarget().isBlank()
				|| evt.getTarget().equalsIgnoreCase(clientName)
				|| channels.contains(evt.getTarget()));
		if (!isAddressedToUs) {
			return;
		}

		String target = evt.getSource();
		if (target == null || target.isBlank()) {
			target = evt.getTarget();
		}
		if (target == null || target.isBlank()) {
			target = channels.isEmpty() ? null : channels.get(0);
		}

		String normalized = eventName.trim().toLowerCase();
		switch (normalized) {
			case "rfid:get_device_metadata":
			case "get_device_metadata":
			case "rfid:get_device_info":
			case "get_device_info":
			case "rfid:device_metadata":
			case "device_metadata":
			case "rfid:get_status":
			case "get_status":
			case "rfid:get_metadata":
			case "get_metadata":
			case "query_device":
			case "device_info":
			case "info":
				logger.info("[WSBroker] Received device metadata command '{}' from '{}'", eventName, evt.getSource());
				sendDeviceMetadata(target, evt.getToken()).exceptionally(ex -> {
					logger.warn("[WSBroker] Failed to send device metadata response: {}", ex.getMessage());
					return null;
				});
				break;
			default:
				logger.debug("[WSBroker] Ignored unrecognized command '{}' from '{}'", eventName, evt.getSource());
				break;
		}
	}

	private void handleGeneralEvent(Event evt) {
		if (evt == null) {
			return;
		}
		// Ignore events from ourselves
		if (clientName != null && clientName.equalsIgnoreCase(evt.getSource())) {
			return;
		}
		// Skip message events as they are processed by handleIncomingMessage
		if (evt.isType(EventType.CLIENT_MESSAGE)) {
			return;
		}
		boolean isTargeted = evt.getTarget() != null && evt.getTarget().equalsIgnoreCase(clientName);
		if (isTargeted) {
			if (evt.isType(EventType.CLIENT_INFO) || evt.isType(EventType.CLIENT_ACTION)) {
				handleMetadataQuery(evt);
			} else if (evt.getMessage() != null && (evt.getMessage().equalsIgnoreCase("get_device_metadata")
					|| evt.getMessage().equalsIgnoreCase("get_device_info")
					|| evt.getMessage().equalsIgnoreCase("device_metadata")
					|| evt.getMessage().equalsIgnoreCase("device_info"))) {
				handleMetadataQuery(evt);
			}
		}
	}

	/**
	 * Sets the connected RFID reader instance for metadata queries.
	 *
	 * @param reader ISO15693 reader instance
	 */
	public void setReader(ISO15693Reader reader) {
		this.reader = reader;
	}

	/**
	 * Returns the currently configured RFID reader instance.
	 *
	 * @return ISO15693 reader instance or null
	 */
	public ISO15693Reader getReader() {
		return reader;
	}

	/**
	 * Queries current device metadata from the connected reader.
	 *
	 * @return DeviceMetadataPayload containing Device ID, Device Type, and Init Description
	 */
	public DeviceMetadataPayload queryDeviceMetadata() {
		String devId = (reader != null) ? reader.getDeviceId() : null;
		String devType = (reader != null) ? reader.getDeviceType() : null;
		String initDesc = (reader != null) ? reader.getInitDescription() : null;

		return new DeviceMetadataPayload(devId, devType, initDesc, "", System.currentTimeMillis(), clientName);
	}

	/**
	 * Dispatches current device metadata to all configured WSBroker channels.
	 */
	public void dispatchDeviceMetadata() {
		dispatch(queryDeviceMetadata());
	}

	/**
	 * Dispatches specific device metadata payload to all configured WSBroker channels.
	 *
	 * @param payload device metadata payload
	 */
	public void dispatchDeviceMetadata(DeviceMetadataPayload payload) {
		dispatch(payload);
	}

	/**
	 * Sends device metadata directly as a reply message (type="message") to a target client or channel.
	 *
	 * @param target destination client name or channel
	 * @param token  optional request/correlation token
	 * @return CompletableFuture completing when the message is sent
	 */
	public CompletableFuture<Void> sendDeviceMetadata(String target, String token) {
		if (!active || client == null) {
			return CompletableFuture.completedFuture(null);
		}
		DeviceMetadataPayload payload = queryDeviceMetadata();
		Event event = Event.createMessage(clientName, target, payload);
		if (token != null && !token.isBlank()) {
			event.setToken(token);
		}
		event.setMessage(payload.getSummaryMessage());
		if (token != null && !token.isBlank()) {
			logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}', token: '{}')",
					target, payload.getDeviceId(), payload.getDeviceType(), token);
		} else {
			logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}')",
					target, payload.getDeviceId(), payload.getDeviceType());
		}
		return client.send(event);
	}

	/**
	 * Handles incoming WebSocket device metadata query events and sends response back.
	 *
	 * @param incomingEvent incoming event requesting metadata
	 */
	public void handleMetadataQuery(Event incomingEvent) {
		if (incomingEvent == null) {
			return;
		}
		if (clientName != null && clientName.equalsIgnoreCase(incomingEvent.getSource())) {
			return;
		}
		logger.info("[WSBroker] Received device metadata query from '{}' (action='{}')",
				incomingEvent.getSource(), incomingEvent.getActions());
		String target = incomingEvent.getSource();
		if (target == null || target.isBlank()) {
			target = channels.isEmpty() ? null : channels.get(0);
		}
		sendDeviceMetadata(target, incomingEvent.getToken()).exceptionally(ex -> {
			logger.warn("[WSBroker] Failed to send device metadata response: {}", ex.getMessage());
			return null;
		});
	}

	public boolean isActive() {
		return active;
	}

	public boolean isConnected() {
		return active && client != null && client.isConnected();
	}

	public String getClientName() {
		return clientName;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public List<String> getChannels() {
		return channels;
	}

	public CertManagerConfig getCertConfig() {
		return certConfig;
	}

	public CertLoader getCertLoader() {
		return certLoader;
	}

	public WSBrokerClient getClient() {
		return client;
	}

	@Override
	public void close() {
		if (client != null) {
			try {
				client.close();
				logger.info("WSBrokerClient closed.");
			} catch (Exception e) {
				logger.warn("Error closing WSBrokerClient: {}", e.getMessage());
			}
		}
		if (certLoader != null) {
			try {
				certLoader.close();
				logger.info("WSBroker CertLoader closed.");
			} catch (Exception e) {
				logger.warn("Error closing CertLoader: {}", e.getMessage());
			}
		}
	}
}

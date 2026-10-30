package org.objectspace.rfid.headless;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.TagsClearedPayload;
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.WSBroker;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages WebSocket broker connection lifecycle and acts as a facade dispatching
 * RFID tag events, metadata queries, and tag write commands to specialized services.
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

	private final HeadlessReaderRegistry registry;
	private final RfidWriteService writeService;
	private final DeviceMetadataService metadataService;
	private final WsBrokerCommandRouter router;

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
		List<String> globalChannels = HeadlessConfigHelper.getChannels(config);
		CertManagerConfig tlsConfig = HeadlessConfigHelper.getClientTlsConfig(config);

		java.util.LinkedHashSet<String> allChannels = new java.util.LinkedHashSet<>();
		if (globalChannels != null) {
			allChannels.addAll(globalChannels);
		}
		List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
		if (readerConfigs != null) {
			for (HeadlessReaderConfig rCfg : readerConfigs) {
				if (rCfg.getChannels() != null) {
					allChannels.addAll(rCfg.getChannels());
				}
			}
		}

		return new HeadlessWsBrokerDispatcher(clientName, endpoint, new ArrayList<>(allChannels), tlsConfig, active);
	}

	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertManagerConfig certConfig) {
		this(clientName, endpoint, channels, certConfig, endpoint != null && !endpoint.isBlank());
	}

	public HeadlessWsBrokerDispatcher(String clientName, String endpoint, List<String> channels, CertManagerConfig certConfig, boolean active) {
		this.clientName = (clientName != null && !clientName.isBlank()) ? clientName.trim() : HeadlessConfigHelper.DEFAULT_CLIENT_NAME;
		this.endpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint.trim() : null;
		this.channels = (channels != null) ? Collections.unmodifiableList(new ArrayList<>(new java.util.LinkedHashSet<>(channels))) : Collections.emptyList();
		this.certConfig = certConfig;

		this.registry = new HeadlessReaderRegistry();
		this.writeService = new RfidWriteService(this.registry, this.clientName);
		this.metadataService = new DeviceMetadataService(this.clientName);

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
			this.router = new WsBrokerCommandRouter(
					this.clientName,
					this.channels,
					this.registry,
					this.writeService,
					this.metadataService,
					() -> this.client,
					() -> this.active
			);
			registerDefaultHandlers();
			logger.info("WSBroker Dispatcher initialized: endpoint={}, clientName={}, channels={}",
					this.endpoint, this.clientName, this.channels);
		} else {
			this.active = false;
			this.certLoader = null;
			this.client = null;
			this.router = new WsBrokerCommandRouter(
					this.clientName,
					this.channels,
					this.registry,
					this.writeService,
					this.metadataService,
					() -> this.client,
					() -> this.active
			);
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
		this.channels = (channels != null) ? Collections.unmodifiableList(new ArrayList<>(new java.util.LinkedHashSet<>(channels))) : Collections.emptyList();
		this.certConfig = null;
		this.certLoader = certLoader;
		this.client = client;
		this.active = active && (endpoint != null && client != null);

		this.registry = new HeadlessReaderRegistry();
		this.writeService = new RfidWriteService(this.registry, this.clientName);
		this.metadataService = new DeviceMetadataService(this.clientName);
		this.router = new WsBrokerCommandRouter(
				this.clientName,
				this.channels,
				this.registry,
				this.writeService,
				this.metadataService,
				() -> this.client,
				() -> this.active
		);

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
	 * Dispatches an RFID event payload to configured WSBroker channels.
	 * If the payload contains a readerId with dedicated channels, only those channels are targeted.
	 * Otherwise, falls back to the globally configured channels.
	 */
	public void dispatch(BaseRfidPayload payload) {
		if (!active || client == null || payload == null) {
			return;
		}

		List<String> targetChannels = getTargetChannelsForPayload(payload);
		if (targetChannels.isEmpty()) {
			return;
		}

		String summaryMsg = payload.getSummaryMessage();

		for (String channel : targetChannels) {
			try {
				Event<BaseRfidPayload> event = Event.createMessage(clientName, channel, payload);
				if (payload.getSession() != null && !payload.getSession().isBlank()) {
					event.setSession(payload.getSession());
				}
				if (payload.getTimestamp() > 0) {
					event.setTime(java.time.Instant.ofEpochMilli(payload.getTimestamp()));
				}
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
	 * Determines the target WSBroker channels for a given payload based on its readerId.
	 */
	public List<String> getTargetChannelsForPayload(BaseRfidPayload payload) {
		if (payload != null && payload.getReaderId() != null && !payload.getReaderId().isBlank()) {
			HeadlessReaderInstance instance = getReaderInstance(payload.getReaderId());
			if (instance != null && instance.getConfig() != null) {
				List<String> instanceChannels = instance.getConfig().getChannels();
				if (instanceChannels != null && !instanceChannels.isEmpty()) {
					return instanceChannels;
				}
			}
		}
		return channels;
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
		dispatchTag(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, metadata, null);
	}

	/**
	 * Dispatches a scanned RFID tag with AFI to all configured WSBroker channels.
	 */
	public void dispatchTag(String uid, String itemId, String rawHex, String isil, String country,
							boolean crcOk, String tagName, String sessionName, long timestamp,
							String clientId, FinnishDataModel metadata, String afi) {
		dispatchTag(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, clientId, metadata, afi);
	}

	/**
	 * Dispatches a scanned RFID tag with readerId and AFI to all configured WSBroker channels.
	 */
	public void dispatchTag(String uid, String itemId, String rawHex, String isil, String country,
							boolean crcOk, String tagName, String sessionName, long timestamp,
							String clientId, String readerId, FinnishDataModel metadata, String afi) {
		dispatch(new TagReadPayload(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, readerId, metadata, afi));
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
	 * Dispatches a reader cleared event with readerId to all configured WSBroker channels.
	 */
	public void dispatchEmpty(String sessionName, long timestamp, String clientId, String readerId) {
		dispatch(new TagsClearedPayload(sessionName, timestamp, clientId, readerId));
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
	 * Registers default message and event handlers for device metadata queries and tag write operations.
	 */
	private void registerDefaultHandlers() {
		if (client != null) {
			router.registerDefaultHandlers(client);
		}
	}

	public void handleIncomingMessage(Event<?> evt) {
		router.handleIncomingMessage(evt);
	}

	public void handleGeneralEvent(Event<?> evt) {
		router.handleGeneralEvent(evt);
	}

	public void registerReaderInstance(HeadlessReaderInstance instance) {
		registry.registerReaderInstance(instance);
	}

	public void setReaderInstances(List<HeadlessReaderInstance> instances) {
		registry.setReaderInstances(instances);
	}

	public Map<String, HeadlessReaderInstance> getReaderInstances() {
		return registry.getReaderInstances();
	}

	public HeadlessReaderInstance getReaderInstance(String readerId) {
		return registry.getReaderInstance(readerId);
	}

	public HeadlessReaderInstance resolveTargetReader(Event<?> evt, JsonNode dataNode) {
		return registry.resolveTargetReader(evt, dataNode, clientName, channels);
	}

	public String extractRequestedReaderId(JsonNode dataNode, Event<?> evt) {
		return WsBrokerPayloadParser.extractRequestedReaderId(dataNode, evt, clientName, channels);
	}

	public void setReader(ISO15693Reader reader) {
		registry.setReader(reader, clientName);
	}

	public ISO15693Reader getReader() {
		return registry.getReader();
	}

	public void setScanner(HeadlessReaderScanner scanner) {
		registry.setScanner(scanner, clientName);
	}

	public HeadlessReaderScanner getScanner() {
		return registry.getScanner();
	}

	public void setCallback(HeadlessReaderCallback callback) {
		registry.setCallback(callback, clientName);
	}

	public HeadlessReaderCallback getCallback() {
		return registry.getCallback();
	}

	public <T> T executeExclusive(HeadlessReaderScanner.ScannerTask<T> task) throws Exception {
		return registry.executeExclusive(task);
	}

	public static Byte parseAfi(Object afiObj) {
		return WsBrokerPayloadParser.parseAfi(afiObj);
	}

	public static String formatAfiHex(Byte afi) {
		return WsBrokerPayloadParser.formatAfiHex(afi);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTag(String uid, String itemId, String isil, String country,
															   int parts, int partNo, int usageType, Object afiObj,
															   String target, String token) {
		return writeTag(uid, itemId, isil, country, parts, partNo, usageType, afiObj, target, token, null);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTag(String uid, String itemId, String isil, String country,
															   int parts, int partNo, int usageType, Object afiObj,
															   String target, String token, String refId) {
		TagWriteItem item = new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afiObj);
		return writeTags(List.of(item), target, token, refId);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTag(List<TagWriteItem> items, String target, String token) {
		return writeTags(items, target, token, null);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTag(List<TagWriteItem> items, String target, String token, String refId) {
		return writeTags(items, target, token, refId);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTags(List<TagWriteItem> items, String target, String token) {
		return writeTags(items, target, token, null);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTags(List<TagWriteItem> items, String target, String token, String refId) {
		return writeTagsOnReader(resolveTargetReader(null, null), items, target, token, refId);
	}

	public CompletableFuture<WriteTagResponsePayload> writeTagsOnReader(
			HeadlessReaderInstance readerInstance,
			List<TagWriteItem> items,
			String target,
			String token,
			String refId) {
		return writeService.writeTagsOnReader(readerInstance, items, target, token, refId, client, active);
	}

	public CompletableFuture<WriteAfiResponsePayload> writeAfi(String uid, Object afiObj, String target, String token) {
		return writeAfi(uid, afiObj, target, token, null);
	}

	public CompletableFuture<WriteAfiResponsePayload> writeAfi(String uid, Object afiObj, String target, String token, String refId) {
		List<String> uids = (uid != null && !uid.isBlank()) ? List.of(uid.trim()) : Collections.emptyList();
		return writeAfi(uids, afiObj, target, token, refId);
	}

	public CompletableFuture<WriteAfiResponsePayload> writeAfi(List<String> uids, Object afiObj, String target, String token) {
		return writeAfi(uids, afiObj, target, token, null);
	}

	public CompletableFuture<WriteAfiResponsePayload> writeAfi(List<String> uids, Object afiObj, String target, String token, String refId) {
		return writeAfiOnReader(resolveTargetReader(null, null), uids, afiObj, target, token, refId);
	}

	public CompletableFuture<WriteAfiResponsePayload> writeAfiOnReader(
			HeadlessReaderInstance readerInstance,
			List<String> uids,
			Object afiObj,
			String target,
			String token,
			String refId) {
		return writeService.writeAfiOnReader(readerInstance, uids, afiObj, target, token, refId, client, active);
	}

	public void handleWriteTagCommand(Event<?> evt, String target) {
		router.handleWriteTagCommand(evt, target);
	}

	public void handleWriteAfiCommand(Event<?> evt, String target) {
		router.handleWriteAfiCommand(evt, target);
	}

	public CompletableFuture<Void> sendReply(String target, BaseRfidPayload payload) {
		return router.sendReply(target, payload);
	}

	public CompletableFuture<Void> sendReply(String target, String refId, BaseRfidPayload payload) {
		return router.sendReply(target, refId, payload);
	}

	public DeviceMetadataPayload queryDeviceMetadata() {
		return queryDeviceMetadata((HeadlessReaderInstance) null);
	}

	public DeviceMetadataPayload queryDeviceMetadata(String readerId) {
		HeadlessReaderInstance inst = (readerId != null) ? getReaderInstance(readerId) : null;
		return queryDeviceMetadata(inst);
	}

	public DeviceMetadataPayload queryDeviceMetadata(HeadlessReaderInstance inst) {
		return metadataService.queryDeviceMetadata(inst, registry.getReader());
	}

	public CompletableFuture<Void> sendDeviceMetadata(String target, String refId) {
		return sendDeviceMetadata(target, refId, null);
	}

	public CompletableFuture<Void> sendDeviceMetadata(String target, String refId, HeadlessReaderInstance inst) {
		return metadataService.sendDeviceMetadata(target, refId, inst, client, active, registry.getReader());
	}

	public void handleMetadataQuery(Event<?> incomingEvent) {
		router.handleMetadataQuery(incomingEvent);
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

	public HeadlessReaderRegistry getRegistry() {
		return registry;
	}

	public RfidWriteService getWriteService() {
		return writeService;
	}

	public DeviceMetadataService getMetadataService() {
		return metadataService;
	}

	public WsBrokerCommandRouter getRouter() {
		return router;
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

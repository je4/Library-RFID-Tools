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
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload;
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
	private volatile HeadlessReaderScanner scanner;
	private volatile HeadlessReaderCallback callback;

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
	 * Registers default message and event handlers for device metadata queries and tag write operations.
	 */
	private void registerDefaultHandlers() {
		if (client == null) {
			return;
		}

		// 1. Primary handler: onMessage compatible with wsbrokerdisplay Controller pattern
		client.onType(EventType.CLIENT_MESSAGE, this::handleIncomingMessage);

		// 2. General event listener fallback for non-message targeted queries
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

			case "rfid:write_tag":
			case "write_tag":
			case "rfid:writetag":
			case "writetag":
			case "rfid:write":
			case "write":
				logger.info("[WSBroker] Received write_tag command '{}' from '{}'", eventName, evt.getSource());
				handleWriteTagCommand(evt, target);
				break;

			case "rfid:write_afi":
			case "write_afi":
				logger.info("[WSBroker] Received write_afi command '{}' from '{}'", eventName, evt.getSource());
				handleWriteAfiCommand(evt, target);
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
			if (evt.isType(EventType.CLIENT_INFO)) {
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
	 * Sets the connected RFID reader instance for metadata queries and write operations.
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
	 * Sets the background scanner loop instance for coordinating write pausing.
	 *
	 * @param scanner scanner instance
	 */
	public void setScanner(HeadlessReaderScanner scanner) {
		this.scanner = scanner;
	}

	/**
	 * Returns the currently configured scanner loop instance.
	 *
	 * @return scanner instance or null
	 */
	public HeadlessReaderScanner getScanner() {
		return scanner;
	}

	/**
	 * Sets the headless reader callback for clearing the debounce cache after write operations.
	 *
	 * @param callback callback instance
	 */
	public void setCallback(HeadlessReaderCallback callback) {
		this.callback = callback;
	}

	/**
	 * Returns the currently configured callback instance.
	 *
	 * @return callback instance or null
	 */
	public HeadlessReaderCallback getCallback() {
		return callback;
	}

	/**
	 * Executes an exclusive task on the RFID reader.
	 * If a scanner instance is present, delegating to scanner.executeExclusive() guarantees that
	 * the scanning loop is safely paused, timing delays are respected, and the debounce cache is cleared.
	 *
	 * @param task task to execute
	 * @param <T>  result type
	 * @return task result
	 * @throws Exception if execution fails
	 */
	public <T> T executeExclusive(HeadlessReaderScanner.ScannerTask<T> task) throws Exception {
		if (scanner != null) {
			return scanner.executeExclusive(task);
		} else if (reader != null) {
			synchronized (reader) {
				Thread.sleep(100);
				T res = task.run(reader);
				Thread.sleep(150);
				if (callback != null) {
					callback.clearDebounceCache();
				}
				return res;
			}
		} else {
			throw new IllegalStateException("No RFID reader configured for write operation");
		}
	}

	/**
	 * Flexible parser for AFI byte parameters (supports Integer, hex string e.g. '07' or '0x07', decimal string).
	 *
	 * @param afiObj AFI parameter object or null
	 * @return parsed Byte or null if missing/empty
	 */
	public static Byte parseAfi(Object afiObj) {
		if (afiObj == null) {
			return null;
		}
		if (afiObj instanceof Number) {
			return ((Number) afiObj).byteValue();
		}
		String s = afiObj.toString().trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.startsWith("0x") || s.startsWith("0X")) {
			return (byte) Integer.parseInt(s.substring(2), 16);
		}
		try {
			return (byte) Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return (byte) Integer.parseInt(s, 16);
		}
	}

	/**
	 * Formats an AFI byte into a 2-digit uppercase hex string (e.g. '07', 'C2').
	 *
	 * @param afi AFI byte value
	 * @return hex string or null
	 */
	public static String formatAfiHex(Byte afi) {
		if (afi == null) {
			return null;
		}
		return String.format("%02X", afi.byteValue() & 0xFF);
	}

	/**
	 * Writes ISO 28560 metadata (item ID, ISIL, country, parts) and optionally AFI to an RFID tag by UID.
	 * Pauses scanning loop during write and clears the scan debounce cache.
	 *
	 * @param uid       target tag UID (or empty/wildcard for single tag in field)
	 * @param itemId    primary item identifier
	 * @param isil      library ISIL identifier
	 * @param country   country code
	 * @param parts     total parts count (default 1)
	 * @param partNo    current part number (default 1)
	 * @param usageType usage type (default 1)
	 * @param afiObj    optional AFI byte or hex string
	 * @param target    target client or channel to send response message to
	 * @param token     correlation token
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTag(String uid, String itemId, String isil, String country,
															   int parts, int partNo, int usageType, Object afiObj,
															   String target, String token) {
		CompletableFuture<WriteTagResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			String effectiveUid = (uid != null) ? uid.trim() : "";
			Byte parsedAfi = parseAfi(afiObj);
			String afiHex = formatAfiHex(parsedAfi);

			try {
				FinnishDataModel model = new FinnishDataModel();
				int effParts = parts > 0 ? parts : 1;
				int effPartNo = partNo > 0 ? partNo : 1;
				int effUsage = usageType > 0 ? usageType : 1;
				model.setValues(effUsage, effParts, effPartNo, (itemId != null ? itemId : ""),
						(country != null ? country : ""), (isil != null ? isil : ""), null);

				executeExclusive(r -> {
					r.writeTag(effectiveUid, model, parsedAfi);
					return null;
				});

				if (callback != null && !effectiveUid.isEmpty()) {
					callback.getActivePresenceMap().put(effectiveUid, System.currentTimeMillis());
				}

				logger.info("[WSBroker] Successfully wrote tag: UID='{}', ItemID='{}', ISIL='{}', Country='{}', AFI='{}'",
						effectiveUid, itemId, isil, country, afiHex);
				return WriteTagResponsePayload.success(effectiveUid, itemId, isil, country,
						effParts, effPartNo, effUsage, afiHex, "", clientName);
			} catch (Exception e) {
				logger.error("[WSBroker] Error writing tag UID='{}': {}", effectiveUid, e.getMessage(), e);
				return WriteTagResponsePayload.error(effectiveUid, itemId, e.getMessage(), "", clientName);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (token != null && !token.isBlank()) {
					reply.setToken(token);
				}
				reply.setMessage(payload.getSummaryMessage());
				return client.send(reply).thenApply(v -> payload);
			}
			return CompletableFuture.completedFuture(payload);
		});
	}

	/**
	 * Writes Application Family Identifier (AFI) byte to an RFID tag by UID.
	 * Pauses scanning loop during write and clears the scan debounce cache.
	 *
	 * @param uid    target tag UID
	 * @param afiObj AFI byte or hex string (e.g. '07', '0x07', 7)
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @return CompletableFuture with WriteAfiResponsePayload
	 */
	public CompletableFuture<WriteAfiResponsePayload> writeAfi(String uid, Object afiObj, String target, String token) {
		CompletableFuture<WriteAfiResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			String effectiveUid = (uid != null) ? uid.trim() : "";
			Byte parsedAfi = parseAfi(afiObj);
			if (parsedAfi == null) {
				return WriteAfiResponsePayload.error(effectiveUid, null, "AFI parameter is missing or invalid", "", clientName);
			}
			String afiHex = formatAfiHex(parsedAfi);

			try {
				executeExclusive(r -> {
					r.writeAfi(effectiveUid, parsedAfi);
					return null;
				});

				if (callback != null && !effectiveUid.isEmpty()) {
					callback.getActivePresenceMap().put(effectiveUid, System.currentTimeMillis());
				}

				logger.info("[WSBroker] Successfully wrote AFI='{}' (0x{}) on tag UID='{}'", parsedAfi, afiHex, effectiveUid);
				return WriteAfiResponsePayload.success(effectiveUid, afiHex, "", clientName);
			} catch (Exception e) {
				logger.error("[WSBroker] Error writing AFI on tag UID='{}': {}", effectiveUid, e.getMessage(), e);
				return WriteAfiResponsePayload.error(effectiveUid, afiHex, e.getMessage(), "", clientName);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (token != null && !token.isBlank()) {
					reply.setToken(token);
				}
				reply.setMessage(payload.getSummaryMessage());
				return client.send(reply).thenApply(v -> payload);
			}
			return CompletableFuture.completedFuture(payload);
		});
	}

	private void handleWriteTagCommand(Event evt, String target) {
		JsonNode data = evt.getData();
		String uid = null;
		String itemId = null;
		String isil = null;
		String country = null;
		int parts = 1;
		int partNo = 1;
		int usageType = 1;
		Object afi = null;

		if (data != null && data.isObject()) {
			if (data.hasNonNull("uid")) uid = data.get("uid").asText();
			else if (data.hasNonNull("UID")) uid = data.get("UID").asText();

			if (data.hasNonNull("itemId")) itemId = data.get("itemId").asText();
			else if (data.hasNonNull("imtemId")) itemId = data.get("imtemId").asText();
			else if (data.hasNonNull("primaryItemId")) itemId = data.get("primaryItemId").asText();
			else if (data.hasNonNull("item_id")) itemId = data.get("item_id").asText();
			else if (data.hasNonNull("barcode")) itemId = data.get("barcode").asText();

			if (data.hasNonNull("isil")) isil = data.get("isil").asText();
			else if (data.hasNonNull("ISIL")) isil = data.get("ISIL").asText();

			if (data.hasNonNull("country")) country = data.get("country").asText();
			else if (data.hasNonNull("countryOfOwnerLib")) country = data.get("countryOfOwnerLib").asText();

			if (data.hasNonNull("parts")) parts = data.get("parts").asInt(1);
			else if (data.hasNonNull("partsInItem")) parts = data.get("partsInItem").asInt(1);

			if (data.hasNonNull("partno")) partNo = data.get("partno").asInt(1);
			else if (data.hasNonNull("partNo")) partNo = data.get("partNo").asInt(1);
			else if (data.hasNonNull("partNumber")) partNo = data.get("partNumber").asInt(1);

			if (data.hasNonNull("usageType")) usageType = data.get("usageType").asInt(1);
			else if (data.hasNonNull("typeOfUsage")) usageType = data.get("typeOfUsage").asInt(1);

			if (data.hasNonNull("afi")) {
				JsonNode afiNode = data.get("afi");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			} else if (data.hasNonNull("AFI")) {
				JsonNode afiNode = data.get("AFI");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			}
		}

		boolean isDirect = evt.getTarget() != null && evt.getTarget().equalsIgnoreCase(clientName);
		String reqUid = (uid != null) ? uid.trim() : "";
		boolean isWildcard = reqUid.isEmpty() || reqUid.equals("*");

		if (callback != null) {
			if (!isDirect) {
				// Broadcast write command: filter silently if tag is not physically present
				if (!isWildcard) {
					if (!callback.isTagPresent(reqUid)) {
						logger.debug("[WSBroker] Broadcast write_tag command for UID '{}' ignored - tag not present on this station ({})",
								reqUid, clientName);
						return;
					}
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_tag command ignored - expected exactly 1 present tag, but found {} on station '{}'",
								presentCount, clientName);
						return;
					}
					uid = callback.getPresentUids().iterator().next();
					logger.info("[WSBroker] Broadcast wildcard write_tag command matched single present tag UID '{}' on station '{}'", uid, clientName);
				}
			} else {
				// Direct command addressed to this station
				if (!isWildcard) {
					if (!callback.isTagPresent(reqUid)) {
						logger.warn("[WSBroker] Direct write_tag command for UID '{}' failed - tag not present on station '{}'", reqUid, clientName);
						WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
								reqUid, (itemId != null ? itemId : ""), "Tag '" + reqUid + "' not present on reader station", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					}
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount == 0) {
						logger.warn("[WSBroker] Direct wildcard write_tag command failed - no tags present on station '{}'", clientName);
						WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
								"", (itemId != null ? itemId : ""), "No RFID tag present on reader station", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					} else if (presentCount > 1) {
						logger.warn("[WSBroker] Direct wildcard write_tag command failed - {} tags present on station '{}'", presentCount, clientName);
						WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
								"", (itemId != null ? itemId : ""), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					} else {
						uid = callback.getPresentUids().iterator().next();
					}
				}
			}
		}

		writeTag(uid, itemId, isil, country, parts, partNo, usageType, afi, target, evt.getToken());
	}

	private void handleWriteAfiCommand(Event evt, String target) {
		JsonNode data = evt.getData();
		String uid = null;
		Object afi = null;

		if (data != null && data.isObject()) {
			if (data.hasNonNull("uid")) uid = data.get("uid").asText();
			else if (data.hasNonNull("UID")) uid = data.get("UID").asText();

			if (data.hasNonNull("afi")) {
				JsonNode afiNode = data.get("afi");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			} else if (data.hasNonNull("AFI")) {
				JsonNode afiNode = data.get("AFI");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			}
		}

		boolean isDirect = evt.getTarget() != null && evt.getTarget().equalsIgnoreCase(clientName);
		String reqUid = (uid != null) ? uid.trim() : "";
		boolean isWildcard = reqUid.isEmpty() || reqUid.equals("*");
		Byte parsedAfi = parseAfi(afi);
		String afiHex = formatAfiHex(parsedAfi);

		if (callback != null) {
			if (!isDirect) {
				// Broadcast write_afi command: filter silently if tag is not physically present
				if (!isWildcard) {
					if (!callback.isTagPresent(reqUid)) {
						logger.debug("[WSBroker] Broadcast write_afi command for UID '{}' ignored - tag not present on this station ({})",
								reqUid, clientName);
						return;
					}
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_afi command ignored - expected exactly 1 present tag, but found {} on station '{}'",
								presentCount, clientName);
						return;
					}
					uid = callback.getPresentUids().iterator().next();
					logger.info("[WSBroker] Broadcast wildcard write_afi command matched single present tag UID '{}' on station '{}'", uid, clientName);
				}
			} else {
				// Direct command addressed to this station
				if (!isWildcard) {
					if (!callback.isTagPresent(reqUid)) {
						logger.warn("[WSBroker] Direct write_afi command for UID '{}' failed - tag not present on station '{}'", reqUid, clientName);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								reqUid, (afiHex != null ? afiHex : ""), "Tag '" + reqUid + "' not present on reader station", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					}
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount == 0) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - no tags present on station '{}'", clientName);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "No RFID tag present on reader station", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					} else if (presentCount > 1) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - {} tags present on station '{}'", presentCount, clientName);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName);
						sendReply(target, evt.getToken(), errPayload);
						return;
					} else {
						uid = callback.getPresentUids().iterator().next();
					}
				}
			}
		}

		writeAfi(uid, afi, target, evt.getToken());
	}

	private CompletableFuture<Void> sendReply(String target, String token, BaseRfidPayload payload) {
		if (active && client != null && target != null && !target.isBlank()) {
			Event reply = Event.createMessage(clientName, target, payload);
			if (token != null && !token.isBlank()) {
				reply.setToken(token);
			}
			reply.setMessage(payload.getSummaryMessage());
			return client.send(reply);
		}
		return CompletableFuture.completedFuture(null);
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
		logger.info("[WSBroker] Received device metadata query from '{}'", incomingEvent.getSource());
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

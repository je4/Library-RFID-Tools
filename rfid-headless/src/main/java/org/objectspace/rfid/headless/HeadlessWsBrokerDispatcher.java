package org.objectspace.rfid.headless;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.TagsClearedPayload;
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload.TagWriteResult;
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
		dispatchTag(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, metadata, null);
	}

	/**
	 * Dispatches a scanned RFID tag with AFI to all configured WSBroker channels.
	 */
	public void dispatchTag(String uid, String itemId, String rawHex, String isil, String country,
							boolean crcOk, String tagName, String sessionName, long timestamp,
							String clientId, FinnishDataModel metadata, String afi) {
		dispatch(new TagReadPayload(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, metadata, afi));
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
				sendDeviceMetadata(target, evt.getToken(), evt.getId()).exceptionally(ex -> {
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
	 * the scanning loop is safely paused, timing delays are respected, and the tags cleared notification is sent.
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
					try {
						callback.empty();
					} catch (Exception e) {
						logger.warn("Error invoking callback.empty() after write operation: {}", e.getMessage());
					}
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
	 * Delegates to {@link #writeTags(List, String, String, String)}.
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
		return writeTag(uid, itemId, isil, country, parts, partNo, usageType, afiObj, target, token, null);
	}

	/**
	 * Writes ISO 28560 metadata (item ID, ISIL, country, parts) and optionally AFI to an RFID tag by UID with RefID correlation.
	 * Delegates to {@link #writeTags(List, String, String, String)}.
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
	 * @param refId     correlated request event ID
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTag(String uid, String itemId, String isil, String country,
															   int parts, int partNo, int usageType, Object afiObj,
															   String target, String token, String refId) {
		TagWriteItem item = new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afiObj);
		return writeTags(List.of(item), target, token, refId);
	}

	/**
	 * Writes ISO 28560 metadata and optional AFI to multiple RFID tags by list of {@link TagWriteItem} specifications.
	 * Delegates to {@link #writeTags(List, String, String, String)}.
	 *
	 * @param items  list of tag write items
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTag(List<TagWriteItem> items, String target, String token) {
		return writeTags(items, target, token, null);
	}

	/**
	 * Writes ISO 28560 metadata and optional AFI to multiple RFID tags by list of {@link TagWriteItem} specifications with RefID correlation.
	 * Delegates to {@link #writeTags(List, String, String, String)}.
	 *
	 * @param items  list of tag write items
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @param refId  correlated request event ID
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTag(List<TagWriteItem> items, String target, String token, String refId) {
		return writeTags(items, target, token, refId);
	}

	/**
	 * Writes ISO 28560 metadata and optional AFI to multiple RFID tags by list of {@link TagWriteItem} specifications.
	 * Pauses scanning loop during batch write and clears the scan debounce cache once for the entire batch.
	 *
	 * @param items  list of tag write items
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTags(List<TagWriteItem> items, String target, String token) {
		return writeTags(items, target, token, null);
	}

	/**
	 * Writes ISO 28560 metadata and optional AFI to multiple RFID tags by list of {@link TagWriteItem} specifications with RefID correlation.
	 * Pauses scanning loop during batch write and clears the scan debounce cache once for the entire batch.
	 *
	 * @param items  list of tag write items
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @param refId  correlated request event ID
	 * @return CompletableFuture with WriteTagResponsePayload
	 */
	public CompletableFuture<WriteTagResponsePayload> writeTags(List<TagWriteItem> items, String target, String token, String refId) {
		CompletableFuture<WriteTagResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			List<TagWriteItem> effectiveItems = new ArrayList<>();
			if (items != null) {
				for (TagWriteItem it : items) {
					if (it != null) {
						effectiveItems.add(it);
					}
				}
			}

			if (effectiveItems.isEmpty()) {
				return WriteTagResponsePayload.error("", "", "No tag items provided for write operation", "", clientName);
			}

			List<String> targetUids = new ArrayList<>();
			List<String> successfulUids = new ArrayList<>();
			Map<String, String> failedUids = new LinkedHashMap<>();
			List<TagWriteResult> results = new ArrayList<>();

			// If single item with wildcard/empty UID, resolve present tag if available
			if (effectiveItems.size() == 1) {
				TagWriteItem single = effectiveItems.get(0);
				String u = single.getUid() != null ? single.getUid().trim() : "";
				if (u.isEmpty() || u.equals("*")) {
					if (callback != null && callback.getPresentTagCount() > 0) {
						u = callback.getPresentUids().iterator().next();
					}
					effectiveItems.set(0, new TagWriteItem(u, single.getItemId(), single.getIsil(),
							single.getCountry(), single.getParts(), single.getPartNo(), single.getUsageType(), single.getAfi()));
				}
			}

			for (TagWriteItem it : effectiveItems) {
				String effectiveUid = it.getUid() != null ? it.getUid().trim() : "";
				if (!targetUids.contains(effectiveUid)) {
					targetUids.add(effectiveUid);
				}
			}

			try {
				executeExclusive(r -> {
					for (int i = 0; i < effectiveItems.size(); i++) {
						TagWriteItem item = effectiveItems.get(i);
						String effectiveUid = item.getUid() != null ? item.getUid().trim() : "";
						Byte parsedAfi = parseAfi(item.getAfi());
						String afiHex = formatAfiHex(parsedAfi);

						try {
							FinnishDataModel model = new FinnishDataModel();
							int effParts = item.getParts() > 0 ? item.getParts() : 1;
							int effPartNo = item.getPartNo() > 0 ? item.getPartNo() : 1;
							int effUsage = item.getUsageType() > 0 ? item.getUsageType() : 1;
							model.setValues(effUsage, effParts, effPartNo,
									(item.getItemId() != null ? item.getItemId() : ""),
									(item.getCountry() != null ? item.getCountry() : ""),
									(item.getIsil() != null ? item.getIsil() : ""),
									null);

							r.writeTag(effectiveUid, model, parsedAfi);
							successfulUids.add(effectiveUid);
							results.add(TagWriteResult.ok(effectiveUid, item.getItemId(), afiHex));
							logger.info("[WSBroker] Successfully wrote tag: UID='{}', ItemID='{}', ISIL='{}', Country='{}', AFI='{}'",
									effectiveUid, item.getItemId(), item.getIsil(), item.getCountry(), afiHex);
						} catch (Exception e) {
							logger.error("[WSBroker] Error writing tag UID='{}': {}", effectiveUid, e.getMessage(), e);
							String errMsg = e.getMessage() != null ? e.getMessage() : "Write failed";
							failedUids.put(effectiveUid, errMsg);
							results.add(TagWriteResult.error(effectiveUid, item.getItemId(), afiHex, errMsg));
						}

						if (i < effectiveItems.size() - 1) {
							try {
								Thread.sleep(50);
							} catch (InterruptedException ie) {
								Thread.currentThread().interrupt();
							}
						}
					}
					return null;
				});

				if (effectiveItems.size() == 1 && successfulUids.size() == 1) {
					TagWriteItem single = effectiveItems.get(0);
					Byte parsedAfi = parseAfi(single.getAfi());
					String afiHex = formatAfiHex(parsedAfi);
					return WriteTagResponsePayload.success(single.getUid(), single.getItemId(), single.getIsil(),
							single.getCountry(), single.getParts(), single.getPartNo(), single.getUsageType(),
							afiHex, "", clientName);
				} else if (effectiveItems.size() == 1 && !failedUids.isEmpty()) {
					TagWriteItem single = effectiveItems.get(0);
					String err = failedUids.get(single.getUid());
					return WriteTagResponsePayload.error(single.getUid(), single.getItemId(), err, "", clientName);
				} else {
					return WriteTagResponsePayload.batchResult(targetUids, successfulUids, failedUids, results, "", clientName);
				}
			} catch (Exception e) {
				logger.error("[WSBroker] Error executing exclusive batch write tags: {}", e.getMessage(), e);
				for (TagWriteItem item : effectiveItems) {
					String uid = item.getUid() != null ? item.getUid().trim() : "";
					if (!successfulUids.contains(uid) && !failedUids.containsKey(uid)) {
						String errMsg = e.getMessage() != null ? e.getMessage() : "Reader error";
						failedUids.put(uid, errMsg);
						Byte parsedAfi = parseAfi(item.getAfi());
						String afiHex = formatAfiHex(parsedAfi);
						results.add(TagWriteResult.error(uid, item.getItemId(), afiHex, errMsg));
					}
				}
				if (effectiveItems.size() == 1) {
					TagWriteItem single = effectiveItems.get(0);
					return WriteTagResponsePayload.error(single.getUid(), single.getItemId(), e.getMessage(), "", clientName);
				}
				return WriteTagResponsePayload.batchResult(targetUids, successfulUids, failedUids, results, "", clientName);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (token != null && !token.isBlank()) {
					reply.setToken(token);
				}
				if (refId != null && !refId.isBlank()) {
					reply.setRefId(refId);
				}
				reply.setMessage(payload.getSummaryMessage());
				return client.send(reply).thenApply(v -> payload);
			}
			return CompletableFuture.completedFuture(payload);
		});
	}

	/**
	 * Writes Application Family Identifier (AFI) byte to an RFID tag by UID.
	 * Delegates to {@link #writeAfi(List, Object, String, String, String)}.
	 *
	 * @param uid    target tag UID
	 * @param afiObj AFI byte or hex string (e.g. '07', '0x07', 7)
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @return CompletableFuture with WriteAfiResponsePayload
	 */
	public CompletableFuture<WriteAfiResponsePayload> writeAfi(String uid, Object afiObj, String target, String token) {
		return writeAfi(uid, afiObj, target, token, null);
	}

	/**
	 * Writes Application Family Identifier (AFI) byte to an RFID tag by UID with RefID correlation.
	 * Delegates to {@link #writeAfi(List, Object, String, String, String)}.
	 *
	 * @param uid    target tag UID
	 * @param afiObj AFI byte or hex string (e.g. '07', '0x07', 7)
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @param refId  correlated request event ID
	 * @return CompletableFuture with WriteAfiResponsePayload
	 */
	public CompletableFuture<WriteAfiResponsePayload> writeAfi(String uid, Object afiObj, String target, String token, String refId) {
		List<String> uids = (uid != null && !uid.isBlank()) ? List.of(uid.trim()) : Collections.emptyList();
		return writeAfi(uids, afiObj, target, token, refId);
	}

	/**
	 * Writes Application Family Identifier (AFI) byte to one or more RFID tags by UID list.
	 * Pauses scanning loop during batch write and clears the scan debounce cache once for the entire batch.
	 *
	 * @param uids   list of target tag UIDs
	 * @param afiObj AFI byte or hex string (e.g. '07', '0x07', 7)
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @return CompletableFuture with WriteAfiResponsePayload
	 */
	public CompletableFuture<WriteAfiResponsePayload> writeAfi(List<String> uids, Object afiObj, String target, String token) {
		return writeAfi(uids, afiObj, target, token, null);
	}

	/**
	 * Writes Application Family Identifier (AFI) byte to one or more RFID tags by UID list with RefID correlation.
	 * Pauses scanning loop during batch write and clears the scan debounce cache once for the entire batch.
	 *
	 * @param uids   list of target tag UIDs
	 * @param afiObj AFI byte or hex string (e.g. '07', '0x07', 7)
	 * @param target target client or channel to send response message to
	 * @param token  correlation token
	 * @param refId  correlated request event ID
	 * @return CompletableFuture with WriteAfiResponsePayload
	 */
	public CompletableFuture<WriteAfiResponsePayload> writeAfi(List<String> uids, Object afiObj, String target, String token, String refId) {
		CompletableFuture<WriteAfiResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			List<String> targetUids = new ArrayList<>();
			if (uids != null) {
				for (String u : uids) {
					if (u != null && !u.isBlank()) {
						String trimmed = u.trim();
						if (!targetUids.contains(trimmed)) {
							targetUids.add(trimmed);
						}
					}
				}
			}

			Byte parsedAfi = parseAfi(afiObj);
			if (parsedAfi == null) {
				String primaryUid = !targetUids.isEmpty() ? targetUids.get(0) : "";
				return WriteAfiResponsePayload.error(primaryUid, null, "AFI parameter is missing or invalid", "", clientName);
			}
			String afiHex = formatAfiHex(parsedAfi);

			if (targetUids.isEmpty()) {
				// Wildcard / single tag if not specified
				if (callback != null && callback.getPresentTagCount() > 0) {
					targetUids.addAll(callback.getPresentUids());
				} else {
					targetUids.add(""); // Empty string for wildcard tag search on reader
				}
			}

			List<String> successfulUids = new ArrayList<>();
			Map<String, String> failedUids = new LinkedHashMap<>();

			try {
				executeExclusive(r -> {
					for (int i = 0; i < targetUids.size(); i++) {
						String effectiveUid = targetUids.get(i);
						try {
							r.writeAfi(effectiveUid, parsedAfi);
							successfulUids.add(effectiveUid);
							logger.info("[WSBroker] Successfully wrote AFI='{}' (0x{}) on tag UID='{}'", parsedAfi, afiHex, effectiveUid);
						} catch (Exception e) {
							logger.error("[WSBroker] Error writing AFI on tag UID='{}': {}", effectiveUid, e.getMessage(), e);
							failedUids.put(effectiveUid, e.getMessage() != null ? e.getMessage() : "Write failed");
						}
						if (i < targetUids.size() - 1) {
							// Ensure settling delay between consecutive transponder writes
							try {
								Thread.sleep(50);
							} catch (InterruptedException ie) {
								Thread.currentThread().interrupt();
							}
						}
					}
					return null;
				});

				return WriteAfiResponsePayload.batchResult(targetUids, successfulUids, failedUids, afiHex, "", clientName);
			} catch (Exception e) {
				logger.error("[WSBroker] Error executing exclusive batch write AFI: {}", e.getMessage(), e);
				for (String effectiveUid : targetUids) {
					if (!successfulUids.contains(effectiveUid) && !failedUids.containsKey(effectiveUid)) {
						failedUids.put(effectiveUid, e.getMessage() != null ? e.getMessage() : "Reader error");
					}
				}
				return WriteAfiResponsePayload.batchResult(targetUids, successfulUids, failedUids, afiHex, "", clientName);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (token != null && !token.isBlank()) {
					reply.setToken(token);
				}
				if (refId != null && !refId.isBlank()) {
					reply.setRefId(refId);
				}
				reply.setMessage(payload.getSummaryMessage());
				return client.send(reply).thenApply(v -> payload);
			}
			return CompletableFuture.completedFuture(payload);
		});
	}

	private void handleWriteTagCommand(Event evt, String target) {
		JsonNode data = evt.getData();
		List<TagWriteItem> items = parseTagWriteItems(data);

		if (items.isEmpty()) {
			logger.warn("[WSBroker] Received write_tag command with no valid tag data from '{}'", evt.getSource());
			WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
					"", "", "No tag data provided in write_tag command", "", clientName);
			sendReply(target, evt.getToken(), evt.getId(), errPayload);
			return;
		}

		boolean isDirect = evt.getTarget() != null && evt.getTarget().equalsIgnoreCase(clientName);
		boolean isSingle = items.size() == 1;
		boolean isWildcard = isSingle && (items.get(0).getUid().isEmpty() || items.get(0).getUid().equals("*"));

		if (callback != null) {
			if (!isDirect) {
				// Broadcast write command: filter silently if tag(s) not physically present
				if (!isWildcard) {
					List<TagWriteItem> presentItems = items.stream()
							.filter(it -> it.getUid().isEmpty() || callback.isTagPresent(it.getUid()))
							.toList();
					if (presentItems.isEmpty()) {
						logger.debug("[WSBroker] Broadcast write_tag command ignored - no matching tags present on station ({})", clientName);
						return;
					}
					items = new ArrayList<>(presentItems);
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_tag command ignored - expected exactly 1 present tag, but found {} on station '{}'",
								presentCount, clientName);
						return;
					}
					String singleUid = callback.getPresentUids().iterator().next();
					TagWriteItem orig = items.get(0);
					items = List.of(new TagWriteItem(singleUid, orig.getItemId(), orig.getIsil(),
							orig.getCountry(), orig.getParts(), orig.getPartNo(), orig.getUsageType(), orig.getAfi()));
					logger.info("[WSBroker] Broadcast wildcard write_tag command matched single present tag UID '{}' on station '{}'", singleUid, clientName);
				}
			} else {
				// Direct command addressed to this station
				if (isSingle) {
					TagWriteItem single = items.get(0);
					if (!isWildcard) {
						if (!callback.isTagPresent(single.getUid())) {
							logger.warn("[WSBroker] Direct write_tag command for UID '{}' failed - tag not present on station '{}'", single.getUid(), clientName);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									single.getUid(), single.getItemId(), "Tag '" + single.getUid() + "' not present on reader station", "", clientName);
							sendReply(target, evt.getToken(), evt.getId(), errPayload);
							return;
						}
					} else {
						int presentCount = callback.getPresentTagCount();
						if (presentCount == 0) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - no tags present on station '{}'", clientName);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									"", single.getItemId(), "No RFID tag present on reader station", "", clientName);
							sendReply(target, evt.getToken(), evt.getId(), errPayload);
							return;
						} else if (presentCount > 1) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - {} tags present on station '{}'", presentCount, clientName);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									"", single.getItemId(), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName);
							sendReply(target, evt.getToken(), evt.getId(), errPayload);
							return;
						} else {
							String singleUid = callback.getPresentUids().iterator().next();
							items = List.of(new TagWriteItem(singleUid, single.getItemId(), single.getIsil(),
									single.getCountry(), single.getParts(), single.getPartNo(), single.getUsageType(), single.getAfi()));
						}
					}
				}
			}
		}

		writeTags(items, target, evt.getToken(), evt.getId());
	}

	private List<TagWriteItem> parseTagWriteItems(JsonNode data) {
		List<TagWriteItem> items = new ArrayList<>();
		if (data == null || data.isNull()) {
			return items;
		}

		if (data.isArray()) {
			int index = 0;
			for (JsonNode child : data) {
				TagWriteItem item = parseSingleTagWriteItem(child, null, null, index + 1);
				if (item != null) {
					items.add(item);
				}
				index++;
			}
			return items;
		}

		if (data.isObject()) {
			// 1. Check array or map wrapper keys
			String[] arrayKeys = {"tags", "items", "transponders", "elements", "list", "data"};
			for (String key : arrayKeys) {
				if (data.hasNonNull(key)) {
					JsonNode node = data.get(key);
					if (node.isArray()) {
						int index = 0;
						for (JsonNode child : node) {
							TagWriteItem item = parseSingleTagWriteItem(child, data, null, index + 1);
							if (item != null) {
								items.add(item);
							}
							index++;
						}
						if (!items.isEmpty()) {
							return items;
						}
					} else if (node.isObject()) {
						boolean isMap = false;
						Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
						int index = 0;
						while (fields.hasNext()) {
							Map.Entry<String, JsonNode> entry = fields.next();
							if (entry.getValue().isObject()) {
								TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, entry.getKey(), index + 1);
								if (item != null) {
									items.add(item);
									isMap = true;
								}
							} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
								items.add(new TagWriteItem(
										entry.getKey(),
										entry.getValue().asText(),
										extractString(data, null, "isil", "ISIL"),
										extractString(data, null, "country", "countryOfOwnerLib"),
										extractInt(data, null, 1, "parts", "partsInItem"),
										extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
										extractInt(data, null, 1, "usageType", "typeOfUsage"),
										extractAfi(data, null)));
								isMap = true;
							}
							index++;
						}
						if (isMap && !items.isEmpty()) {
							return items;
						}
					}
				}
			}

			// 2. Check if uids / UIDs is present (as array or map)
			if ((data.hasNonNull("uids") && (data.get("uids").isArray() || data.get("uids").isObject())) ||
				(data.hasNonNull("UIDs") && (data.get("UIDs").isArray() || data.get("UIDs").isObject()))) {
				JsonNode uidsNode = data.hasNonNull("uids") ? data.get("uids") : data.get("UIDs");
				if (uidsNode.isArray()) {
					JsonNode itemIdsNode = null;
					String[] itemArrayKeys = {"itemIds", "item_ids", "barcodes", "items"};
					for (String k : itemArrayKeys) {
						if (data.hasNonNull(k) && data.get(k).isArray()) {
							itemIdsNode = data.get(k);
							break;
						}
					}
					JsonNode afisNode = null;
					String[] afiArrayKeys = {"afis", "AFIs", "afiList"};
					for (String k : afiArrayKeys) {
						if (data.hasNonNull(k) && data.get(k).isArray()) {
							afisNode = data.get(k);
							break;
						}
					}

					int index = 0;
					int totalCount = uidsNode.size();
					for (JsonNode child : uidsNode) {
						if (child.isTextual() && !child.asText().isBlank()) {
							String uid = child.asText().trim();
							String itemId = (itemIdsNode != null && itemIdsNode.size() > index)
									? itemIdsNode.get(index).asText()
									: extractString(data, null, "itemId", "imtemId", "primaryItemId", "item_id", "barcode", "item");
							Object afi = (afisNode != null && afisNode.size() > index)
									? (afisNode.get(index).isInt() ? afisNode.get(index).asInt() : afisNode.get(index).asText())
									: extractAfi(data, null);
							String isil = extractString(data, null, "isil", "ISIL");
							String country = extractString(data, null, "country", "countryOfOwnerLib");
							int parts = extractInt(data, null, totalCount > 1 ? totalCount : 1, "parts", "partsInItem");
							int partNo = extractInt(data, null, index + 1, "partno", "partNo", "partNumber");
							int usageType = extractInt(data, null, 1, "usageType", "typeOfUsage");
							items.add(new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afi));
						} else if (child.isObject()) {
							TagWriteItem item = parseSingleTagWriteItem(child, data, null, index + 1);
							if (item != null) {
								items.add(item);
							}
						}
						index++;
					}
					if (!items.isEmpty()) {
						return items;
					}
				} else if (uidsNode.isObject()) {
					int index = 0;
					Iterator<Map.Entry<String, JsonNode>> fields = uidsNode.fields();
					while (fields.hasNext()) {
						Map.Entry<String, JsonNode> entry = fields.next();
						if (entry.getValue().isObject()) {
							TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, entry.getKey(), index + 1);
							if (item != null) {
								items.add(item);
							}
						} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
							items.add(new TagWriteItem(
									entry.getKey(),
									entry.getValue().asText(),
									extractString(data, null, "isil", "ISIL"),
									extractString(data, null, "country", "countryOfOwnerLib"),
									extractInt(data, null, 1, "parts", "partsInItem"),
									extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
									extractInt(data, null, 1, "usageType", "typeOfUsage"),
									extractAfi(data, null)));
						}
						index++;
					}
					if (!items.isEmpty()) {
						return items;
					}
				}
			}

			// 3. Check if root data itself is a Map of UID -> object / UID -> itemId
			boolean hasDirectTagFields = data.hasNonNull("uid") || data.hasNonNull("UID") ||
					data.hasNonNull("itemId") || data.hasNonNull("imtemId") || data.hasNonNull("barcode") ||
					data.hasNonNull("primaryItemId") || data.hasNonNull("item_id");

			if (!hasDirectTagFields) {
				Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
				List<TagWriteItem> mapItems = new ArrayList<>();
				int index = 0;
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> entry = fields.next();
					String key = entry.getKey();
					if ("event".equalsIgnoreCase(key) || "session".equalsIgnoreCase(key) ||
							"timestamp".equalsIgnoreCase(key) || "clientId".equalsIgnoreCase(key) ||
							"isil".equalsIgnoreCase(key) || "country".equalsIgnoreCase(key) ||
							"parts".equalsIgnoreCase(key) || "usageType".equalsIgnoreCase(key) ||
							"afi".equalsIgnoreCase(key) || "target".equalsIgnoreCase(key)) {
						continue;
					}
					if (entry.getValue().isObject()) {
						TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, key, index + 1);
						if (item != null) {
							mapItems.add(item);
						}
					} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
						mapItems.add(new TagWriteItem(
								key,
								entry.getValue().asText(),
								extractString(data, null, "isil", "ISIL"),
								extractString(data, null, "country", "countryOfOwnerLib"),
								extractInt(data, null, 1, "parts", "partsInItem"),
								extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
								extractInt(data, null, 1, "usageType", "typeOfUsage"),
								extractAfi(data, null)));
					}
					index++;
				}
				if (!mapItems.isEmpty()) {
					return mapItems;
				}
			}

			// 4. Single tag object
			TagWriteItem single = parseSingleTagWriteItem(data, null, null, 1);
			if (single != null && (!single.getUid().isEmpty() || !single.getItemId().isEmpty() || single.getAfi() != null)) {
				items.add(single);
			}
		}

		return items;
	}

	private TagWriteItem parseSingleTagWriteItem(JsonNode node, JsonNode defaults, String defaultUid, int defaultPartNo) {
		if (node == null || (!node.isObject() && !node.isTextual())) {
			return null;
		}
		if (node.isTextual()) {
			String u = node.asText().trim();
			return new TagWriteItem(u, "", "", "", 1, defaultPartNo, 1, null);
		}
		String uid = extractString(node, null, "uid", "UID");
		if ((uid == null || uid.isBlank()) && defaultUid != null && !defaultUid.isBlank()) {
			uid = defaultUid.trim();
		}
		String itemId = extractString(node, defaults, "itemId", "imtemId", "primaryItemId", "item_id", "barcode", "item", "data");
		String isil = extractString(node, defaults, "isil", "ISIL");
		String country = extractString(node, defaults, "country", "countryOfOwnerLib");
		int parts = extractInt(node, defaults, 1, "parts", "partsInItem");
		int partNo = extractInt(node, defaults, defaultPartNo, "partno", "partNo", "partNumber");
		int usageType = extractInt(node, defaults, 1, "usageType", "typeOfUsage");
		Object afi = extractAfi(node, defaults);
		return new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afi);
	}

	private static String extractString(JsonNode node, JsonNode defaults, String... keys) {
		if (node != null) {
			for (String k : keys) {
				if (node.hasNonNull(k)) return node.get(k).asText();
			}
		}
		if (defaults != null) {
			for (String k : keys) {
				if (defaults.hasNonNull(k)) return defaults.get(k).asText();
			}
		}
		return "";
	}

	private static int extractInt(JsonNode node, JsonNode defaults, int defaultVal, String... keys) {
		if (node != null) {
			for (String k : keys) {
				if (node.hasNonNull(k)) return node.get(k).asInt(defaultVal);
			}
		}
		if (defaults != null) {
			for (String k : keys) {
				if (defaults.hasNonNull(k)) return defaults.get(k).asInt(defaultVal);
			}
		}
		return defaultVal;
	}

	private static Object extractAfi(JsonNode node, JsonNode defaults) {
		if (node != null) {
			if (node.hasNonNull("afi")) {
				JsonNode n = node.get("afi");
				return n.isInt() ? n.asInt() : n.asText();
			} else if (node.hasNonNull("AFI")) {
				JsonNode n = node.get("AFI");
				return n.isInt() ? n.asInt() : n.asText();
			}
		}
		if (defaults != null) {
			if (defaults.hasNonNull("afi")) {
				JsonNode n = defaults.get("afi");
				return n.isInt() ? n.asInt() : n.asText();
			} else if (defaults.hasNonNull("AFI")) {
				JsonNode n = defaults.get("AFI");
				return n.isInt() ? n.asInt() : n.asText();
			}
		}
		return null;
	}

	private void handleWriteAfiCommand(Event evt, String target) {
		JsonNode data = evt.getData();
		List<String> targetUids = new ArrayList<>();
		Object afi = null;

		if (data != null && data.isObject()) {
			if (data.hasNonNull("uids") && data.get("uids").isArray()) {
				for (JsonNode uNode : data.get("uids")) {
					if (uNode.isTextual() && !uNode.asText().isBlank()) {
						targetUids.add(uNode.asText().trim());
					}
				}
			} else if (data.hasNonNull("UIDs") && data.get("UIDs").isArray()) {
				for (JsonNode uNode : data.get("UIDs")) {
					if (uNode.isTextual() && !uNode.asText().isBlank()) {
						targetUids.add(uNode.asText().trim());
					}
				}
			} else if (data.hasNonNull("uid")) {
				String u = data.get("uid").asText();
				if (u != null && !u.isBlank()) targetUids.add(u.trim());
			} else if (data.hasNonNull("UID")) {
				String u = data.get("UID").asText();
				if (u != null && !u.isBlank()) targetUids.add(u.trim());
			}

			if (data.hasNonNull("afi")) {
				JsonNode afiNode = data.get("afi");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			} else if (data.hasNonNull("AFI")) {
				JsonNode afiNode = data.get("AFI");
				afi = afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			}
		}

		boolean isDirect = evt.getTarget() != null && evt.getTarget().equalsIgnoreCase(clientName);
		boolean isWildcard = targetUids.isEmpty() || (targetUids.size() == 1 && (targetUids.get(0).isEmpty() || targetUids.get(0).equals("*")));
		Byte parsedAfi = parseAfi(afi);
		String afiHex = formatAfiHex(parsedAfi);

		if (callback != null) {
			if (!isDirect) {
				// Broadcast write_afi command: filter silently if tag is not physically present
				if (!isWildcard) {
					List<String> presentUids = targetUids.stream().filter(callback::isTagPresent).toList();
					if (presentUids.isEmpty()) {
						logger.debug("[WSBroker] Broadcast write_afi command for UIDs {} ignored - none present on this station ({})",
								targetUids, clientName);
						return;
					}
					targetUids = new ArrayList<>(presentUids);
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_afi command ignored - expected exactly 1 present tag, but found {} on station '{}'",
								presentCount, clientName);
						return;
					}
					String singleUid = callback.getPresentUids().iterator().next();
					targetUids = List.of(singleUid);
					logger.info("[WSBroker] Broadcast wildcard write_afi command matched single present tag UID '{}' on station '{}'", singleUid, clientName);
				}
			} else {
				// Direct command addressed to this station
				if (!isWildcard) {
					if (targetUids.size() == 1) {
						String reqUid = targetUids.get(0);
						if (!callback.isTagPresent(reqUid)) {
							logger.warn("[WSBroker] Direct write_afi command for UID '{}' failed - tag not present on station '{}'", reqUid, clientName);
							WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
									reqUid, (afiHex != null ? afiHex : ""), "Tag '" + reqUid + "' not present on reader station", "", clientName);
							sendReply(target, evt.getToken(), evt.getId(), errPayload);
							return;
						}
					}
				} else {
					int presentCount = callback.getPresentTagCount();
					if (presentCount == 0) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - no tags present on station '{}'", clientName);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "No RFID tag present on reader station", "", clientName);
						sendReply(target, evt.getToken(), evt.getId(), errPayload);
						return;
					} else if (presentCount > 1) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - {} tags present on station '{}'", presentCount, clientName);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName);
						sendReply(target, evt.getToken(), evt.getId(), errPayload);
						return;
					} else {
						targetUids = List.of(callback.getPresentUids().iterator().next());
					}
				}
			}
		}

		writeAfi(targetUids, afi, target, evt.getToken(), evt.getId());
	}

	private CompletableFuture<Void> sendReply(String target, String token, BaseRfidPayload payload) {
		return sendReply(target, token, null, payload);
	}

	private CompletableFuture<Void> sendReply(String target, String token, String refId, BaseRfidPayload payload) {
		if (active && client != null && target != null && !target.isBlank()) {
			Event reply = Event.createMessage(clientName, target, payload);
			if (token != null && !token.isBlank()) {
				reply.setToken(token);
			}
			if (refId != null && !refId.isBlank()) {
				reply.setRefId(refId);
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
		return sendDeviceMetadata(target, token, null);
	}

	/**
	 * Sends device metadata directly as a reply message (type="message") to a target client or channel with RefID correlation.
	 *
	 * @param target destination client name or channel
	 * @param token  optional request/correlation token
	 * @param refId  correlated request event ID
	 * @return CompletableFuture completing when the message is sent
	 */
	public CompletableFuture<Void> sendDeviceMetadata(String target, String token, String refId) {
		if (!active || client == null) {
			return CompletableFuture.completedFuture(null);
		}
		DeviceMetadataPayload payload = queryDeviceMetadata();
		Event event = Event.createMessage(clientName, target, payload);
		if (token != null && !token.isBlank()) {
			event.setToken(token);
		}
		if (refId != null && !refId.isBlank()) {
			event.setRefId(refId);
		}
		event.setMessage(payload.getSummaryMessage());
		if (token != null && !token.isBlank()) {
			logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}', token: '{}', refId: '{}')",
					target, payload.getDeviceId(), payload.getDeviceType(), token, refId);
		} else {
			logger.info("[WSBroker] Sending device metadata response to '{}' (device: '{}', type: '{}', refId: '{}')",
					target, payload.getDeviceId(), payload.getDeviceType(), refId);
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
		sendDeviceMetadata(target, incomingEvent.getToken(), incomingEvent.getId()).exceptionally(ex -> {
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

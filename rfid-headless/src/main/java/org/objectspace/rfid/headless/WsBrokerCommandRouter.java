package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.objectspace.rfid.headless.payload.BaseRfidPayload;
import org.objectspace.rfid.headless.payload.DeviceMetadataPayload;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Routes and handles incoming WSBroker events (messages, metadata queries, tag write, AFI write).
 */
public class WsBrokerCommandRouter {

	private static final Logger logger = LoggerFactory.getLogger(WsBrokerCommandRouter.class);

	private final String clientName;
	private final List<String> channels;
	private final HeadlessReaderRegistry registry;
	private final RfidWriteService writeService;
	private final DeviceMetadataService metadataService;
	private final Supplier<WSBrokerClient> clientSupplier;
	private final BooleanSupplier activeSupplier;

	public WsBrokerCommandRouter(
			String clientName,
			List<String> channels,
			HeadlessReaderRegistry registry,
			RfidWriteService writeService,
			DeviceMetadataService metadataService,
			Supplier<WSBrokerClient> clientSupplier,
			BooleanSupplier activeSupplier) {
		this.clientName = clientName;
		this.channels = channels != null ? channels : List.of();
		this.registry = registry;
		this.writeService = writeService;
		this.metadataService = metadataService;
		this.clientSupplier = clientSupplier;
		this.activeSupplier = activeSupplier;
	}

	public void registerDefaultHandlers(WSBrokerClient client) {
		if (client == null) {
			return;
		}
		client.onType(EventType.CLIENT_MESSAGE, this::handleIncomingMessage);
		client.onEvent(this::handleGeneralEvent);
	}

	public void handleIncomingMessage(Event<?> evt) {
		if (evt == null) {
			return;
		}

		if (clientName != null && clientName.equalsIgnoreCase(evt.getSource())) {
			return;
		}

		JsonNode dataNode = evt.getDataAs(JsonNode.class);
		String eventName = WsBrokerPayloadParser.extractEventName(evt, dataNode);

		if (eventName == null || eventName.isBlank() || eventName.toLowerCase().endsWith("_response")) {
			return;
		}

		if (!WsBrokerPayloadParser.isAddressedToUs(evt, clientName, channels, registry.getReaderInstances())) {
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
				handleMetadataQuery(evt);
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

	public void handleGeneralEvent(Event<?> evt) {
		if (evt == null) {
			return;
		}
		if (clientName != null && clientName.equalsIgnoreCase(evt.getSource())) {
			return;
		}
		if (evt.isType(EventType.CLIENT_MESSAGE)) {
			return;
		}
		boolean isTargeted = evt.getTarget() != null && (evt.getTarget().equalsIgnoreCase(clientName)
				|| registry.getReaderInstances().containsKey(evt.getTarget().toLowerCase()));
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

	public void handleMetadataQuery(Event<?> incomingEvent) {
		if (incomingEvent == null) {
			return;
		}
		if (clientName != null && clientName.equalsIgnoreCase(incomingEvent.getSource())) {
			return;
		}

		JsonNode dataNode = incomingEvent.getDataAs(JsonNode.class);
		HeadlessReaderInstance targetReader = registry.resolveTargetReader(incomingEvent, dataNode, clientName, channels);
		String reqReaderId = WsBrokerPayloadParser.extractRequestedReaderId(dataNode, incomingEvent, clientName, channels);

		String target = incomingEvent.getSource();
		if (target == null || target.isBlank()) {
			target = channels.isEmpty() ? null : channels.get(0);
		}

		if (reqReaderId != null && targetReader == null && !registry.getReaderInstances().isEmpty()) {
			logger.warn("[WSBroker] Device metadata query failed - Reader '{}' not found", reqReaderId);
			DeviceMetadataPayload errPayload = new DeviceMetadataPayload("", "", "Reader '" + reqReaderId + "' not found",
					"", System.currentTimeMillis(), clientName, reqReaderId, "error", DeviceMetadataPayload.EVENT_RESPONSE);
			sendReply(target, incomingEvent.getId(), errPayload);
			return;
		}

		logger.info("[WSBroker] Received device metadata query from '{}'{}", incomingEvent.getSource(),
				(targetReader != null ? " for reader '" + targetReader.getReaderId() + "'" : ""));

		WSBrokerClient client = clientSupplier.get();
		boolean active = activeSupplier.getAsBoolean();
		metadataService.sendDeviceMetadata(target, incomingEvent.getId(), targetReader, client, active, registry.getReader()).exceptionally(ex -> {
			logger.warn("[WSBroker] Failed to send device metadata response: {}", ex.getMessage());
			return null;
		});
	}

	public void handleWriteTagCommand(Event<?> evt, String target) {
		JsonNode data = evt.getDataAs(JsonNode.class);
		List<TagWriteItem> items = WsBrokerPayloadParser.parseTagWriteItems(data);

		HeadlessReaderInstance readerInstance = registry.resolveTargetReader(evt, data, clientName, channels);
		String reqReaderId = WsBrokerPayloadParser.extractRequestedReaderId(data, evt, clientName, channels);

		if (reqReaderId != null && readerInstance == null && !registry.getReaderInstances().isEmpty()) {
			logger.warn("[WSBroker] write_tag failed - Reader '{}' not found", reqReaderId);
			WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
					"", "", "Reader '" + reqReaderId + "' not found", "", clientName, reqReaderId);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		if (readerInstance == null) {
			if (registry.getReader() != null || registry.getScanner() != null) {
				HeadlessReaderConfig cfg = new HeadlessReaderConfig(clientName);
				readerInstance = new HeadlessReaderInstance(cfg, registry.getReader(), registry.getCallback(), registry.getScanner());
			}
		}

		if (readerInstance == null) {
			logger.warn("[WSBroker] write_tag failed - No active reader configured");
			WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
					"", "", "No active RFID reader available", "", clientName);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		String effectiveReaderId = readerInstance.getReaderId();

		// Read-Only Enforcement
		if (readerInstance.isReadOnly()) {
			logger.warn("[WSBroker] Rejecting write_tag command on read-only reader '{}'", effectiveReaderId);
			String primaryUid = !items.isEmpty() ? items.get(0).getUid() : "";
			String primaryItemId = !items.isEmpty() ? items.get(0).getItemId() : "";
			WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
					primaryUid, primaryItemId, "Reader '" + effectiveReaderId + "' is configured as read-only",
					"", clientName, effectiveReaderId);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		if (items.isEmpty()) {
			logger.warn("[WSBroker] Received write_tag command with no valid tag data from '{}'", evt.getSource());
			WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
					"", "", "No tag data provided in write_tag command", "", clientName, effectiveReaderId);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		HeadlessReaderCallback effectiveCallback = readerInstance.getCallback();
		boolean isDirect = evt.getTarget() != null && (evt.getTarget().equalsIgnoreCase(clientName) || evt.getTarget().equalsIgnoreCase(effectiveReaderId));
		boolean isSingle = items.size() == 1;
		boolean isWildcard = isSingle && (items.get(0).getUid().isEmpty() || items.get(0).getUid().equals("*"));

		if (effectiveCallback != null) {
			if (!isDirect) {
				// Broadcast write command: filter silently if tag(s) not physically present
				if (!isWildcard) {
					List<TagWriteItem> presentItems = items.stream()
							.filter(it -> it.getUid().isEmpty() || effectiveCallback.isTagPresent(it.getUid()))
							.toList();
					if (presentItems.isEmpty()) {
						logger.debug("[WSBroker] Broadcast write_tag command ignored - no matching tags present on reader '{}'", effectiveReaderId);
						return;
					}
					items = new ArrayList<>(presentItems);
				} else {
					int presentCount = effectiveCallback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_tag command ignored - expected exactly 1 present tag, but found {} on reader '{}'",
								presentCount, effectiveReaderId);
						return;
					}
					String singleUid = effectiveCallback.getPresentUids().iterator().next();
					TagWriteItem orig = items.get(0);
					items = List.of(new TagWriteItem(singleUid, orig.getItemId(), orig.getIsil(),
							orig.getCountry(), orig.getParts(), orig.getPartNo(), orig.getUsageType(), orig.getAfi()));
					logger.info("[WSBroker] Broadcast wildcard write_tag command matched single present tag UID '{}' on reader '{}'", singleUid, effectiveReaderId);
				}
			} else {
				// Direct command addressed to this station/reader
				if (isSingle) {
					TagWriteItem single = items.get(0);
					if (!isWildcard) {
						if (!effectiveCallback.isTagPresent(single.getUid())) {
							logger.warn("[WSBroker] Direct write_tag command for UID '{}' failed - tag not present on reader '{}'", single.getUid(), effectiveReaderId);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									single.getUid(), single.getItemId(), "Tag '" + single.getUid() + "' not present on reader station", "", clientName, effectiveReaderId);
							sendReply(target, evt.getId(), errPayload);
							return;
						}
					} else {
						int presentCount = effectiveCallback.getPresentTagCount();
						if (presentCount == 0) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - no tags present on reader '{}'", effectiveReaderId);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									"", single.getItemId(), "No RFID tag present on reader station", "", clientName, effectiveReaderId);
							sendReply(target, evt.getId(), errPayload);
							return;
						} else if (presentCount > 1) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - {} tags present on reader '{}'", presentCount, effectiveReaderId);
							WriteTagResponsePayload errPayload = WriteTagResponsePayload.error(
									"", single.getItemId(), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName, effectiveReaderId);
							sendReply(target, evt.getId(), errPayload);
							return;
						} else {
							String singleUid = effectiveCallback.getPresentUids().iterator().next();
							items = List.of(new TagWriteItem(singleUid, single.getItemId(), single.getIsil(),
									single.getCountry(), single.getParts(), single.getPartNo(), single.getUsageType(), single.getAfi()));
						}
					}
				}
			}
		}

		WSBrokerClient client = clientSupplier.get();
		boolean active = activeSupplier.getAsBoolean();
		writeService.writeTagsOnReader(readerInstance, items, target, null, evt.getId(), client, active);
	}

	public void handleWriteAfiCommand(Event<?> evt, String target) {
		JsonNode data = evt.getDataAs(JsonNode.class);
		List<String> targetUids = WsBrokerPayloadParser.extractTargetUidsForAfi(data);
		Object afi = WsBrokerPayloadParser.extractAfiObject(data);

		HeadlessReaderInstance readerInstance = registry.resolveTargetReader(evt, data, clientName, channels);
		String reqReaderId = WsBrokerPayloadParser.extractRequestedReaderId(data, evt, clientName, channels);

		if (reqReaderId != null && readerInstance == null && !registry.getReaderInstances().isEmpty()) {
			logger.warn("[WSBroker] write_afi failed - Reader '{}' not found", reqReaderId);
			WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
					"", "", "Reader '" + reqReaderId + "' not found", "", clientName, reqReaderId);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		if (readerInstance == null) {
			if (registry.getReader() != null || registry.getScanner() != null) {
				HeadlessReaderConfig cfg = new HeadlessReaderConfig(clientName);
				readerInstance = new HeadlessReaderInstance(cfg, registry.getReader(), registry.getCallback(), registry.getScanner());
			}
		}

		if (readerInstance == null) {
			logger.warn("[WSBroker] write_afi failed - No active reader configured");
			WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
					"", "", "No active RFID reader available", "", clientName);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		String effectiveReaderId = readerInstance.getReaderId();

		Byte parsedAfi = WsBrokerPayloadParser.parseAfi(afi);
		String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);

		// Read-Only Enforcement
		if (readerInstance.isReadOnly()) {
			logger.warn("[WSBroker] Rejecting write_afi command on read-only reader '{}'", effectiveReaderId);
			String primaryUid = !targetUids.isEmpty() ? targetUids.get(0) : "";
			WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
					primaryUid, (afiHex != null ? afiHex : ""), "Reader '" + effectiveReaderId + "' is configured as read-only",
					"", clientName, effectiveReaderId);
			sendReply(target, evt.getId(), errPayload);
			return;
		}

		HeadlessReaderCallback effectiveCallback = readerInstance.getCallback();
		boolean isDirect = evt.getTarget() != null && (evt.getTarget().equalsIgnoreCase(clientName) || evt.getTarget().equalsIgnoreCase(effectiveReaderId));
		boolean isWildcard = targetUids.isEmpty() || (targetUids.size() == 1 && (targetUids.get(0).isEmpty() || targetUids.get(0).equals("*")));

		if (effectiveCallback != null) {
			if (!isDirect) {
				// Broadcast write_afi command: filter silently if tag is not physically present
				if (!isWildcard) {
					List<String> presentUids = targetUids.stream().filter(effectiveCallback::isTagPresent).toList();
					if (presentUids.isEmpty()) {
						logger.debug("[WSBroker] Broadcast write_afi command for UIDs {} ignored - none present on reader '{}'",
								targetUids, effectiveReaderId);
						return;
					}
					targetUids = new ArrayList<>(presentUids);
				} else {
					int presentCount = effectiveCallback.getPresentTagCount();
					if (presentCount != 1) {
						logger.debug("[WSBroker] Broadcast wildcard write_afi command ignored - expected exactly 1 present tag, but found {} on reader '{}'",
								presentCount, effectiveReaderId);
						return;
					}
					String singleUid = effectiveCallback.getPresentUids().iterator().next();
					targetUids = List.of(singleUid);
					logger.info("[WSBroker] Broadcast wildcard write_afi command matched single present tag UID '{}' on reader '{}'", singleUid, effectiveReaderId);
				}
			} else {
				// Direct command addressed to this station/reader
				if (!isWildcard) {
					if (targetUids.size() == 1) {
						String reqUid = targetUids.get(0);
						if (!effectiveCallback.isTagPresent(reqUid)) {
							logger.warn("[WSBroker] Direct write_afi command for UID '{}' failed - tag not present on reader '{}'", reqUid, effectiveReaderId);
							WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
									reqUid, (afiHex != null ? afiHex : ""), "Tag '" + reqUid + "' not present on reader station", "", clientName, effectiveReaderId);
							sendReply(target, evt.getId(), errPayload);
							return;
						}
					}
				} else {
					int presentCount = effectiveCallback.getPresentTagCount();
					if (presentCount == 0) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - no tags present on reader '{}'", effectiveReaderId);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "No RFID tag present on reader station", "", clientName, effectiveReaderId);
						sendReply(target, evt.getId(), errPayload);
						return;
					} else if (presentCount > 1) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - {} tags present on reader '{}'", presentCount, effectiveReaderId);
						WriteAfiResponsePayload errPayload = WriteAfiResponsePayload.error(
								"", (afiHex != null ? afiHex : ""), "Multiple RFID tags present on reader station (" + presentCount + " tags)", "", clientName, effectiveReaderId);
						sendReply(target, evt.getId(), errPayload);
						return;
					} else {
						targetUids = List.of(effectiveCallback.getPresentUids().iterator().next());
					}
				}
			}
		}

		WSBrokerClient client = clientSupplier.get();
		boolean active = activeSupplier.getAsBoolean();
		writeService.writeAfiOnReader(readerInstance, targetUids, afi, target, null, evt.getId(), client, active);
	}

	public CompletableFuture<Void> sendReply(String target, BaseRfidPayload payload) {
		return sendReply(target, (String) null, payload);
	}

	public CompletableFuture<Void> sendReply(String target, String refId, BaseRfidPayload payload) {
		WSBrokerClient client = clientSupplier.get();
		boolean active = activeSupplier.getAsBoolean();
		if (active && client != null && target != null && !target.isBlank()) {
			Event<BaseRfidPayload> reply = Event.createMessage(clientName, target, payload);
			if (payload.getSession() != null && !payload.getSession().isBlank()) {
				reply.setSession(payload.getSession());
			}
			if (payload.getTimestamp() > 0) {
				reply.setTime(java.time.Instant.ofEpochMilli(payload.getTimestamp()));
			}
			if (refId != null && !refId.isBlank()) {
				reply.setRefId(refId);
			}
			reply.setMessage(payload.getSummaryMessage());
			return client.send(reply);
		}
		return CompletableFuture.completedFuture(null);
	}
}

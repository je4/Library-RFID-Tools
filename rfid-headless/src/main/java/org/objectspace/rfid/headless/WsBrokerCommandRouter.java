package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.objectspace.rfid.headless.payload.DeviceMetadataData;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponseData;
import org.objectspace.rfid.headless.payload.WriteTagResponseData;
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
		client.onType(DeviceMetadataData.EVENT_NAME, this::handleMetadataQuery);
		client.onType("rfid/device_metadata", this::handleMetadataQuery);
		client.onType(WriteTagResponseData.EVENT_REQUEST, evt -> {
			String target = resolveReplyTarget(evt);
			handleWriteTagCommand(evt, target);
		});
		client.onType(WriteAfiResponseData.EVENT_REQUEST, evt -> {
			String target = resolveReplyTarget(evt);
			handleWriteAfiCommand(evt, target);
		});
		client.onEvent(this::handleGeneralEvent);
	}

	private String resolveReplyTarget(Event<?> evt) {
		String target = evt.getSource();
		if (target == null || target.isBlank()) {
			target = evt.getTarget();
		}
		if (target == null || target.isBlank()) {
			target = channels.isEmpty() ? null : channels.get(0);
		}
		return target;
	}

	public void handleIncomingMessage(Event<?> evt) {
		handleGeneralEvent(evt);
	}

	public void handleGeneralEvent(Event<?> evt) {
		if (evt == null) {
			return;
		}
		if (clientName != null && clientName.equalsIgnoreCase(evt.getSource())) {
			return;
		}
		String eventName = WsBrokerPayloadParser.extractEventName(evt, evt.getDataNode());
		if (eventName == null || eventName.isBlank() || eventName.toLowerCase().endsWith("_response")) {
			return;
		}
		if (!WsBrokerPayloadParser.isAddressedToUs(evt, clientName, channels, registry.getReaderInstances())) {
			return;
		}
		String target = resolveReplyTarget(evt);
		String normalized = eventName.trim().toLowerCase();
		switch (normalized) {
			case "rfid/get_device_metadata":
			case "rfid/device_metadata":
				handleMetadataQuery(evt);
				break;
			case "rfid/write_tag":
				handleWriteTagCommand(evt, target);
				break;
			case "rfid/write_afi":
				handleWriteAfiCommand(evt, target);
				break;
			default:
				break;
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
			DeviceMetadataData errData = new DeviceMetadataData("error", "", "", "Reader '" + reqReaderId + "' not found", reqReaderId);
			sendReply(target, incomingEvent.getId(), DeviceMetadataData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
			WriteTagResponseData errData = WriteTagResponseData.error(
					"", "", "Reader '" + reqReaderId + "' not found", reqReaderId);
			sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
			WriteTagResponseData errData = WriteTagResponseData.error(
					"", "", "No active RFID reader available", clientName);
			sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
			return;
		}

		String effectiveReaderId = readerInstance.getReaderId();

		// Read-Only Enforcement
		if (readerInstance.isReadOnly()) {
			logger.warn("[WSBroker] Rejecting write_tag command on read-only reader '{}'", effectiveReaderId);
			String primaryUid = !items.isEmpty() ? items.get(0).getUid() : "";
			String primaryItemId = !items.isEmpty() ? items.get(0).getItemId() : "";
			WriteTagResponseData errData = WriteTagResponseData.error(
					primaryUid, primaryItemId, "Reader '" + effectiveReaderId + "' is configured as read-only",
					effectiveReaderId);
			sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
			return;
		}

		if (items.isEmpty()) {
			logger.warn("[WSBroker] Received write_tag command with no valid tag data from '{}'", evt.getSource());
			WriteTagResponseData errData = WriteTagResponseData.error(
					"", "", "No tag data provided in write_tag command", effectiveReaderId);
			sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
							WriteTagResponseData errData = WriteTagResponseData.error(
									single.getUid(), single.getItemId(), "Tag '" + single.getUid() + "' not present on reader station", effectiveReaderId);
							sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
							return;
						}
					} else {
						int presentCount = effectiveCallback.getPresentTagCount();
						if (presentCount == 0) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - no tags present on reader '{}'", effectiveReaderId);
							WriteTagResponseData errData = WriteTagResponseData.error(
									"", single.getItemId(), "No RFID tag present on reader station", effectiveReaderId);
							sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
							return;
						} else if (presentCount > 1) {
							logger.warn("[WSBroker] Direct wildcard write_tag command failed - {} tags present on reader '{}'", presentCount, effectiveReaderId);
							WriteTagResponseData errData = WriteTagResponseData.error(
									"", single.getItemId(), "Multiple RFID tags present on reader station (" + presentCount + " tags)", effectiveReaderId);
							sendReply(target, evt.getId(), WriteTagResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
			WriteAfiResponseData errData = WriteAfiResponseData.error(
					"", "", "Reader '" + reqReaderId + "' not found", reqReaderId);
			sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
			WriteAfiResponseData errData = WriteAfiResponseData.error(
					"", "", "No active RFID reader available", clientName);
			sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
			return;
		}

		String effectiveReaderId = readerInstance.getReaderId();

		Byte parsedAfi = WsBrokerPayloadParser.parseAfi(afi);
		String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);

		// Read-Only Enforcement
		if (readerInstance.isReadOnly()) {
			logger.warn("[WSBroker] Rejecting write_afi command on read-only reader '{}'", effectiveReaderId);
			String primaryUid = !targetUids.isEmpty() ? targetUids.get(0) : "";
			WriteAfiResponseData errData = WriteAfiResponseData.error(
					primaryUid, (afiHex != null ? afiHex : ""), "Reader '" + effectiveReaderId + "' is configured as read-only",
					effectiveReaderId);
			sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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
							WriteAfiResponseData errData = WriteAfiResponseData.error(
									reqUid, (afiHex != null ? afiHex : ""), "Tag '" + reqUid + "' not present on reader station", effectiveReaderId);
							sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
							return;
						}
					}
				} else {
					int presentCount = effectiveCallback.getPresentTagCount();
					if (presentCount == 0) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - no tags present on reader '{}'", effectiveReaderId);
						WriteAfiResponseData errData = WriteAfiResponseData.error(
								"", (afiHex != null ? afiHex : ""), "No RFID tag present on reader station", effectiveReaderId);
						sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
						return;
					} else if (presentCount > 1) {
						logger.warn("[WSBroker] Direct wildcard write_afi command failed - {} tags present on reader '{}'", presentCount, effectiveReaderId);
						WriteAfiResponseData errData = WriteAfiResponseData.error(
								"", (afiHex != null ? afiHex : ""), "Multiple RFID tags present on reader station (" + presentCount + " tags)", effectiveReaderId);
						sendReply(target, evt.getId(), WriteAfiResponseData.EVENT_RESPONSE, errData, errData.getSummaryMessage());
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

	public <T> CompletableFuture<Void> sendReply(String target, String type, T payload) {
		return sendReply(target, null, type, payload, null);
	}

	public <T> CompletableFuture<Void> sendReply(String target, String refId, String type, T payload, String summaryMessage) {
		WSBrokerClient client = clientSupplier.get();
		boolean active = activeSupplier.getAsBoolean();
		if (active && client != null && target != null && !target.isBlank()) {
			Event<T> reply = Event.of(type, clientName, target, payload);
			if (refId != null && !refId.isBlank()) {
				reply.setRefId(refId);
			}
			if (summaryMessage != null && !summaryMessage.isBlank()) {
				reply.setMessage(summaryMessage);
			}
			return client.send(reply);
		}
		return CompletableFuture.completedFuture(null);
	}
}

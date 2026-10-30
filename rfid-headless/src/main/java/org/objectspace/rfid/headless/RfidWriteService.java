package org.objectspace.rfid.headless;

import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload;
import org.objectspace.rfid.headless.payload.WriteTagResponsePayload.TagWriteResult;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for executing RFID tag data (ISO 28560) and AFI write operations on readers,
 * coordinating exclusive hardware locking, debounce cache clearing, and response replies.
 */
public class RfidWriteService {

	private static final Logger logger = LoggerFactory.getLogger(RfidWriteService.class);

	private final HeadlessReaderRegistry registry;
	private final String clientName;

	public RfidWriteService(HeadlessReaderRegistry registry, String clientName) {
		this.registry = registry;
		this.clientName = clientName;
	}

	public CompletableFuture<WriteTagResponsePayload> writeTagsOnReader(
			HeadlessReaderInstance readerInstance,
			List<TagWriteItem> items,
			String target,
			String token,
			String refId,
			WSBrokerClient client,
			boolean active) {
		CompletableFuture<WriteTagResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			String effectiveReaderId = readerInstance != null ? readerInstance.getReaderId() : clientName;

			if (readerInstance != null && readerInstance.isReadOnly()) {
				logger.warn("[WSBroker] Rejecting writeTags on read-only reader '{}'", effectiveReaderId);
				String primaryUid = (items != null && !items.isEmpty()) ? items.get(0).getUid() : "";
				String primaryItemId = (items != null && !items.isEmpty()) ? items.get(0).getItemId() : "";
				return WriteTagResponsePayload.error(primaryUid, primaryItemId,
						"Reader '" + effectiveReaderId + "' is configured as read-only", "", clientName, effectiveReaderId);
			}

			List<TagWriteItem> effectiveItems = new ArrayList<>();
			if (items != null) {
				for (TagWriteItem it : items) {
					if (it != null) {
						effectiveItems.add(it);
					}
				}
			}

			if (effectiveItems.isEmpty()) {
				return WriteTagResponsePayload.error("", "", "No tag items provided for write operation", "", clientName, effectiveReaderId);
			}

			List<String> targetUids = new ArrayList<>();
			List<String> successfulUids = new ArrayList<>();
			Map<String, String> failedUids = new LinkedHashMap<>();
			List<TagWriteResult> results = new ArrayList<>();

			HeadlessReaderCallback effectiveCallback = readerInstance != null ? readerInstance.getCallback() : registry.getCallback();

			// If single item with wildcard/empty UID, resolve present tag if available
			if (effectiveItems.size() == 1) {
				TagWriteItem single = effectiveItems.get(0);
				String u = single.getUid() != null ? single.getUid().trim() : "";
				if (u.isEmpty() || u.equals("*")) {
					if (effectiveCallback != null && effectiveCallback.getPresentTagCount() > 0) {
						u = effectiveCallback.getPresentUids().iterator().next();
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
				HeadlessReaderScanner.ScannerTask<Void> task = r -> {
					for (int i = 0; i < effectiveItems.size(); i++) {
						TagWriteItem item = effectiveItems.get(i);
						String effectiveUid = item.getUid() != null ? item.getUid().trim() : "";
						Byte parsedAfi = WsBrokerPayloadParser.parseAfi(item.getAfi());
						String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);

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
							logger.info("[WSBroker] Successfully wrote tag on reader '{}': UID='{}', ItemID='{}', ISIL='{}', Country='{}', AFI='{}'",
									effectiveReaderId, effectiveUid, item.getItemId(), item.getIsil(), item.getCountry(), afiHex);
						} catch (Exception e) {
							logger.error("[WSBroker] Error writing tag on reader '{}' UID='{}': {}", effectiveReaderId, effectiveUid, e.getMessage(), e);
							String errMsg = e.getMessage() != null ? e.getMessage() : "Write failed";
							failedUids.put(effectiveUid, errMsg);
							results.add(TagWriteResult.error(effectiveUid, item.getItemId(), afiHex, errMsg));
						}

						if (i < effectiveItems.size() - 1) {
							// Ensure settling delay between consecutive transponder writes
							try {
								Thread.sleep(50);
							} catch (InterruptedException ie) {
								Thread.currentThread().interrupt();
							}
						}
					}
					return null;
				};

				if (readerInstance != null) {
					readerInstance.executeExclusive(task);
				} else {
					registry.executeExclusive(task);
				}

				if (effectiveItems.size() == 1 && successfulUids.size() == 1) {
					TagWriteItem single = effectiveItems.get(0);
					Byte parsedAfi = WsBrokerPayloadParser.parseAfi(single.getAfi());
					String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);
					return WriteTagResponsePayload.success(single.getUid(), single.getItemId(), single.getIsil(),
							single.getCountry(), single.getParts(), single.getPartNo(), single.getUsageType(),
							afiHex, "", clientName, effectiveReaderId);
				} else if (effectiveItems.size() == 1 && !failedUids.isEmpty()) {
					TagWriteItem single = effectiveItems.get(0);
					String err = failedUids.get(single.getUid());
					return WriteTagResponsePayload.error(single.getUid(), single.getItemId(), err, "", clientName, effectiveReaderId);
				} else {
					return WriteTagResponsePayload.batchResult(targetUids, successfulUids, failedUids, results, "", clientName, effectiveReaderId);
				}
			} catch (Exception e) {
				logger.error("[WSBroker] Error executing exclusive batch write tags on reader '{}': {}", effectiveReaderId, e.getMessage(), e);
				for (TagWriteItem item : effectiveItems) {
					String uid = item.getUid() != null ? item.getUid().trim() : "";
					if (!successfulUids.contains(uid) && !failedUids.containsKey(uid)) {
						String errMsg = e.getMessage() != null ? e.getMessage() : "Reader error";
						failedUids.put(uid, errMsg);
						Byte parsedAfi = WsBrokerPayloadParser.parseAfi(item.getAfi());
						String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);
						results.add(TagWriteResult.error(uid, item.getItemId(), afiHex, errMsg));
					}
				}
				if (effectiveItems.size() == 1) {
					TagWriteItem single = effectiveItems.get(0);
					return WriteTagResponsePayload.error(single.getUid(), single.getItemId(), e.getMessage(), "", clientName, effectiveReaderId);
				}
				return WriteTagResponsePayload.batchResult(targetUids, successfulUids, failedUids, results, "", clientName, effectiveReaderId);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (payload.getSession() != null && !payload.getSession().isBlank()) {
					reply.setSession(payload.getSession());
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

	public CompletableFuture<WriteAfiResponsePayload> writeAfiOnReader(
			HeadlessReaderInstance readerInstance,
			List<String> uids,
			Object afiObj,
			String target,
			String token,
			String refId,
			WSBrokerClient client,
			boolean active) {
		CompletableFuture<WriteAfiResponsePayload> future = CompletableFuture.supplyAsync(() -> {
			String effectiveReaderId = readerInstance != null ? readerInstance.getReaderId() : clientName;

			Byte parsedAfi = WsBrokerPayloadParser.parseAfi(afiObj);
			String afiHex = WsBrokerPayloadParser.formatAfiHex(parsedAfi);

			if (readerInstance != null && readerInstance.isReadOnly()) {
				logger.warn("[WSBroker] Rejecting writeAfi on read-only reader '{}'", effectiveReaderId);
				String primaryUid = (uids != null && !uids.isEmpty()) ? uids.get(0) : "";
				return WriteAfiResponsePayload.error(primaryUid, (afiHex != null ? afiHex : ""),
						"Reader '" + effectiveReaderId + "' is configured as read-only", "", clientName, effectiveReaderId);
			}

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

			if (parsedAfi == null) {
				String primaryUid = !targetUids.isEmpty() ? targetUids.get(0) : "";
				return WriteAfiResponsePayload.error(primaryUid, null, "AFI parameter is missing or invalid", "", clientName, effectiveReaderId);
			}

			HeadlessReaderCallback effectiveCallback = readerInstance != null ? readerInstance.getCallback() : registry.getCallback();

			if (targetUids.isEmpty()) {
				// Wildcard / single tag if not specified
				if (effectiveCallback != null && effectiveCallback.getPresentTagCount() > 0) {
					targetUids.addAll(effectiveCallback.getPresentUids());
				} else {
					targetUids.add(""); // Empty string for wildcard tag search on reader
				}
			}

			List<String> successfulUids = new ArrayList<>();
			Map<String, String> failedUids = new LinkedHashMap<>();

			try {
				HeadlessReaderScanner.ScannerTask<Void> task = r -> {
					for (int i = 0; i < targetUids.size(); i++) {
						String effectiveUid = targetUids.get(i);
						try {
							r.writeAfi(effectiveUid, parsedAfi);
							successfulUids.add(effectiveUid);
							logger.info("[WSBroker] Successfully wrote AFI='{}' (0x{}) on reader '{}' tag UID='{}'",
									parsedAfi, afiHex, effectiveReaderId, effectiveUid);
						} catch (Exception e) {
							logger.error("[WSBroker] Error writing AFI on reader '{}' tag UID='{}': {}",
									effectiveReaderId, effectiveUid, e.getMessage(), e);
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
				};

				if (readerInstance != null) {
					readerInstance.executeExclusive(task);
				} else {
					registry.executeExclusive(task);
				}

				return WriteAfiResponsePayload.batchResult(targetUids, successfulUids, failedUids, afiHex, "", clientName, effectiveReaderId);
			} catch (Exception e) {
				logger.error("[WSBroker] Error executing exclusive batch write AFI on reader '{}': {}", effectiveReaderId, e.getMessage(), e);
				for (String effectiveUid : targetUids) {
					if (!successfulUids.contains(effectiveUid) && !failedUids.containsKey(effectiveUid)) {
						failedUids.put(effectiveUid, e.getMessage() != null ? e.getMessage() : "Reader error");
					}
				}
				return WriteAfiResponsePayload.batchResult(targetUids, successfulUids, failedUids, afiHex, "", clientName, effectiveReaderId);
			}
		});

		return future.thenCompose(payload -> {
			if (active && client != null && target != null && !target.isBlank()) {
				Event reply = Event.createMessage(clientName, target, payload);
				if (payload.getSession() != null && !payload.getSession().isBlank()) {
					reply.setSession(payload.getSession());
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
}

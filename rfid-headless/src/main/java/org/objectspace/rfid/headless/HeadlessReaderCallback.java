package org.objectspace.rfid.headless;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.webservice.WebserviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.feig.fedm.utility.HexConvert;

/**
 * Headless RFID tag callback with time-based debounce deduplication.
 * Processes ISO 28560 metadata, persists to database, and dispatches to REST webservice.
 */
public class HeadlessReaderCallback implements TagCallback {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderCallback.class);

	private final TagScanProcessor tagScanProcessor;
	private final HeadlessWsBrokerDispatcher wsBrokerDispatcher;
	private final long debounceMillis;
	private final ConcurrentHashMap<String, Long> lastSeenMap;
	private final String sessionName;

	private final AtomicLong totalScans = new AtomicLong(0);
	private final AtomicLong dispatchedScans = new AtomicLong(0);
	private final AtomicLong debouncedScans = new AtomicLong(0);
	private final AtomicBoolean hasActiveTags = new AtomicBoolean(false);
	private final AtomicLong clearedEvents = new AtomicLong(0);

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, long debounceMillis) {
		this(tagScanProcessor, null, debounceMillis);
	}

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, HeadlessWsBrokerDispatcher wsBrokerDispatcher, long debounceMillis) {
		this.tagScanProcessor = tagScanProcessor;
		this.wsBrokerDispatcher = wsBrokerDispatcher;
		this.debounceMillis = debounceMillis >= 0 ? debounceMillis : HeadlessConfigHelper.DEFAULT_DEBOUNCE_MILLIS;
		this.lastSeenMap = new ConcurrentHashMap<>();
		this.sessionName = LocalDateTime.now().toString();
		logger.info("HeadlessReaderCallback initialized with debounce window: {} ms, session: {}", this.debounceMillis, this.sessionName);
	}

	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long blockSize) throws Exception {
		hasActiveTags.set(true);
		totalScans.incrementAndGet();

		long now = System.currentTimeMillis();
		Long lastSeen = lastSeenMap.get(UID);
		if (lastSeen != null && (now - lastSeen) < debounceMillis) {
			debouncedScans.incrementAndGet();
			logger.debug("Tag {} ignored (cooldown active, remaining: {} ms)", UID, debounceMillis - (now - lastSeen));
			return null;
		}

		FinnishDataModel metadata = new FinnishDataModel();
		if (data != null && data.length > 0) {
			try {
				metadata.setBlock(data, blockSize);
			} catch (Exception e) {
				logger.warn("Failed to decode FinnishDataModel for UID {}: {}", UID, e.getMessage());
			}
		}

		String itemId = metadata.isEmpty() ? "empty" : metadata.getPrimaryItemId();
		String isil = metadata.getISIL();
		String country = metadata.getCountryOfOwnerLib();
		boolean crcOk = !metadata.getCRCError();

		logger.info("[TAG READ] UID: {} | ItemID: {} | ISIL: {} | Country: {} | CRC: {} | TagType: {}",
				UID, itemId, isil, country, (crcOk ? "OK" : "ERROR"), tagName);

		// 1. Database persistence
		if (tagScanProcessor != null && tagScanProcessor.isDatabaseConnected()) {
			try {
				tagScanProcessor.storeInDatabase(UID, metadata, "", sessionName);
				logger.debug("[DB] Successfully stored UID: {}", UID);
			} catch (Exception e) {
				logger.error("[DB Error] Failed to persist UID {}: {}", UID, e.getMessage(), e);
			}
		}

		// 2. Webservice dispatch
		if (tagScanProcessor != null && tagScanProcessor.isWebserviceActive()) {
			try {
				String rawHex = (metadata.getData() != null) ? HexConvert.toHexString(metadata.getData()) : "";
				WebserviceResponse wsResp = tagScanProcessor.dispatchWebservice(
						"",
						itemId,
						rawHex,
						UID,
						metadata,
						sessionName,
						null
				);
				if (wsResp != null) {
					if (wsResp.isSuccess()) {
						logger.info("[Webservice] HTTP {} ({} ms) for UID: {}", wsResp.getHttpStatus(), wsResp.getDurationMs(), UID);
					} else {
						logger.warn("[Webservice Error] HTTP {} for UID: {} - {}", wsResp.getHttpStatus(), UID, wsResp.getErrorMessage());
						if (tagScanProcessor.getWebserviceConfig() != null && tagScanProcessor.getWebserviceConfig().isDebugMode()) {
							logger.warn("{}", wsResp.formatScanLogDetails());
						}
					}
					logger.debug("[Webservice] URL: {}", wsResp.getRequestUrl());
					logger.debug("[Webservice] Headers: {}", wsResp.getRequestHeaders());
					logger.debug("[Webservice] Body: {}", wsResp.getRequestBody());
				}
			} catch (Exception e) {
				logger.error("[Webservice Exception] Failed to dispatch UID {}: {}", UID, e.getMessage(), e);
			}
		}

		// 3. WSBroker WebSocket channels dispatch
		if (wsBrokerDispatcher != null && wsBrokerDispatcher.isActive()) {
			try {
				String rawHex = (metadata.getData() != null) ? HexConvert.toHexString(metadata.getData()) : "";
				String clientId = (tagScanProcessor != null && tagScanProcessor.getWebserviceConfig() != null)
						? tagScanProcessor.getWebserviceConfig().getClientId()
						: "";
				wsBrokerDispatcher.dispatchTag(
						UID,
						itemId,
						rawHex,
						isil,
						country,
						crcOk,
						tagName,
						sessionName,
						now,
						clientId,
						metadata
				);
			} catch (Exception e) {
				logger.error("[WSBroker Exception] Failed to dispatch UID {}: {}", UID, e.getMessage(), e);
			}
		}

		// 4. Update debounce cache
		lastSeenMap.put(UID, now);
		dispatchedScans.incrementAndGet();

		// Periodic cache pruning if map grows
		if (lastSeenMap.size() > 500) {
			pruneCache(now);
		}

		return null;
	}

	@Override
	public void empty() {
		if (hasActiveTags.compareAndSet(true, false)) {
			clearedEvents.incrementAndGet();
			long now = System.currentTimeMillis();
			logger.info("[READER CLEARED] All RFID tags removed from reader field");
			lastSeenMap.clear();

			if (wsBrokerDispatcher != null && wsBrokerDispatcher.isActive()) {
				try {
					String clientId = (tagScanProcessor != null && tagScanProcessor.getWebserviceConfig() != null)
							? tagScanProcessor.getWebserviceConfig().getClientId()
							: "";
					wsBrokerDispatcher.dispatchEmpty(sessionName, now, clientId);
				} catch (Exception e) {
					logger.error("[WSBroker Exception] Failed to dispatch reader cleared event: {}", e.getMessage(), e);
				}
			}
		}
	}

	@Override
	public void close() throws Exception {
		if (wsBrokerDispatcher != null) {
			try {
				wsBrokerDispatcher.close();
			} catch (Exception e) {
				logger.warn("Error closing WSBroker dispatcher: {}", e.getMessage());
			}
		}
		clearCache();
		logger.info("HeadlessReaderCallback closed.");
	}

	private void pruneCache(long now) {
		long thresholdMillis = debounceMillis > 0 ? (debounceMillis * 2) : 1000L;
		long expiryThreshold = now - thresholdMillis;
		lastSeenMap.entrySet().removeIf(entry -> entry.getValue() < expiryThreshold);
	}

	public HeadlessWsBrokerDispatcher getWsBrokerDispatcher() {
		return wsBrokerDispatcher;
	}

	public long getDebounceMillis() {
		return debounceMillis;
	}

	public ConcurrentHashMap<String, Long> getLastSeenMap() {
		return lastSeenMap;
	}

	public void clearCache() {
		lastSeenMap.clear();
		hasActiveTags.set(false);
	}

	public boolean hasActiveTags() {
		return hasActiveTags.get();
	}

	public long getClearedEvents() {
		return clearedEvents.get();
	}

	public long getTotalScans() {
		return totalScans.get();
	}

	public long getDispatchedScans() {
		return dispatchedScans.get();
	}

	public long getDebouncedScans() {
		return debouncedScans.get();
	}

	public String getSessionName() {
		return sessionName;
	}
}

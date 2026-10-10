package org.objectspace.rfid.raspi;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
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
public class RaspiInventoryCallback implements TagCallback {

	private static final Logger logger = LoggerFactory.getLogger(RaspiInventoryCallback.class);

	private final TagScanProcessor tagScanProcessor;
	private final long debounceMillis;
	private final ConcurrentHashMap<String, Long> lastSeenMap;
	private final String sessionName;

	private final AtomicLong totalScans = new AtomicLong(0);
	private final AtomicLong dispatchedScans = new AtomicLong(0);
	private final AtomicLong debouncedScans = new AtomicLong(0);

	public RaspiInventoryCallback(TagScanProcessor tagScanProcessor, long debounceMillis) {
		this.tagScanProcessor = tagScanProcessor;
		this.debounceMillis = debounceMillis > 0 ? debounceMillis : RaspiConfigHelper.DEFAULT_DEBOUNCE_MILLIS;
		this.lastSeenMap = new ConcurrentHashMap<>();
		this.sessionName = LocalDateTime.now().toString();
		logger.info("RaspiInventoryCallback initialized with debounce window: {} ms, session: {}", this.debounceMillis, this.sessionName);
	}

	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long blockSize) throws Exception {
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
				}
			} catch (Exception e) {
				logger.error("[Webservice Exception] Failed to dispatch UID {}: {}", UID, e.getMessage(), e);
			}
		}

		// 3. Update debounce cache
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
		// Called when no tags are detected in the reader field
	}

	@Override
	public void close() throws Exception {
		clearCache();
		logger.info("RaspiInventoryCallback closed.");
	}

	private void pruneCache(long now) {
		long expiryThreshold = now - (debounceMillis * 2);
		lastSeenMap.entrySet().removeIf(entry -> entry.getValue() < expiryThreshold);
	}

	public long getDebounceMillis() {
		return debounceMillis;
	}

	public ConcurrentHashMap<String, Long> getLastSeenMap() {
		return lastSeenMap;
	}

	public void clearCache() {
		lastSeenMap.clear();
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

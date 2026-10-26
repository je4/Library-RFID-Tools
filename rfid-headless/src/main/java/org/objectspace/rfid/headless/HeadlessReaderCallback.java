package org.objectspace.rfid.headless;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * Headless RFID tag callback with time-based debounce deduplication and active presence tracking.
 * Processes ISO 28560 metadata, persists to database, and dispatches to REST webservice and WSBroker.
 */
public class HeadlessReaderCallback implements TagCallback {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderCallback.class);

	public static final long DEFAULT_PRESENCE_TTL_MILLIS = 1200L;

	private final TagScanProcessor tagScanProcessor;
	private final HeadlessWsBrokerDispatcher wsBrokerDispatcher;
	private final long debounceMillis;
	private final long presenceTtlMillis;
	private final ConcurrentHashMap<String, Long> lastSeenMap;
	private final ConcurrentHashMap<String, Long> activePresenceMap;
	private final String sessionName;
	private final String readerId;

	private final AtomicLong totalScans = new AtomicLong(0);
	private final AtomicLong dispatchedScans = new AtomicLong(0);
	private final AtomicLong debouncedScans = new AtomicLong(0);
	private final AtomicBoolean hasActiveTags = new AtomicBoolean(false);
	private final AtomicLong clearedEvents = new AtomicLong(0);

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, long debounceMillis) {
		this(tagScanProcessor, null, debounceMillis, DEFAULT_PRESENCE_TTL_MILLIS, null);
	}

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, HeadlessWsBrokerDispatcher wsBrokerDispatcher, long debounceMillis) {
		this(tagScanProcessor, wsBrokerDispatcher, debounceMillis, DEFAULT_PRESENCE_TTL_MILLIS, null);
	}

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, HeadlessWsBrokerDispatcher wsBrokerDispatcher,
								  long debounceMillis, long presenceTtlMillis) {
		this(tagScanProcessor, wsBrokerDispatcher, debounceMillis, presenceTtlMillis, null);
	}

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, HeadlessWsBrokerDispatcher wsBrokerDispatcher,
								  long debounceMillis, String readerId) {
		this(tagScanProcessor, wsBrokerDispatcher, debounceMillis, DEFAULT_PRESENCE_TTL_MILLIS, readerId);
	}

	public HeadlessReaderCallback(TagScanProcessor tagScanProcessor, HeadlessWsBrokerDispatcher wsBrokerDispatcher,
								  long debounceMillis, long presenceTtlMillis, String readerId) {
		this.tagScanProcessor = tagScanProcessor;
		this.wsBrokerDispatcher = wsBrokerDispatcher;
		this.debounceMillis = debounceMillis >= 0 ? debounceMillis : HeadlessConfigHelper.DEFAULT_DEBOUNCE_MILLIS;
		this.presenceTtlMillis = presenceTtlMillis > 0 ? presenceTtlMillis : DEFAULT_PRESENCE_TTL_MILLIS;
		this.readerId = (readerId != null && !readerId.isBlank()) ? readerId.trim() : null;
		this.lastSeenMap = new ConcurrentHashMap<>();
		this.activePresenceMap = new ConcurrentHashMap<>();
		this.sessionName = LocalDateTime.now().toString();
		logger.info("HeadlessReaderCallback initialized with debounce window: {} ms, presence TTL: {} ms, session: {}{}",
				this.debounceMillis, this.presenceTtlMillis, this.sessionName,
				(this.readerId != null ? ", readerId: " + this.readerId : ""));
	}

	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long blockSize, String afi) throws Exception {
		hasActiveTags.set(true);
		totalScans.incrementAndGet();

		long now = System.currentTimeMillis();
		String normalizedUid = (UID != null) ? UID.trim() : "";
		if (!normalizedUid.isEmpty()) {
			activePresenceMap.put(normalizedUid, now);
		}

		Long lastSeen = !normalizedUid.isEmpty() ? lastSeenMap.get(normalizedUid) : null;
		if (lastSeen != null && (now - lastSeen) < debounceMillis) {
			debouncedScans.incrementAndGet();
			logger.debug("Tag {} ignored (cooldown active, remaining: {} ms)", normalizedUid, debounceMillis - (now - lastSeen));
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

		String readerPrefix = (readerId != null && !readerId.isBlank()) ? "Reader: " + readerId + " | " : "";
		if (afi != null && !afi.isEmpty()) {
			logger.info("[TAG READ] {}UID: {} | ItemID: {} | ISIL: {} | Country: {} | CRC: {} | AFI: {} | TagType: {}",
					readerPrefix, UID, itemId, isil, country, (crcOk ? "OK" : "ERROR"), afi, tagName);
		} else {
			logger.info("[TAG READ] {}UID: {} | ItemID: {} | ISIL: {} | Country: {} | CRC: {} | TagType: {}",
					readerPrefix, UID, itemId, isil, country, (crcOk ? "OK" : "ERROR"), tagName);
		}

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
				String effectiveReaderId = (readerId != null && !readerId.isBlank()) ? readerId : clientId;
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
						effectiveReaderId,
						metadata,
						afi
				);
			} catch (Exception e) {
				logger.error("[WSBroker Exception] Failed to dispatch UID {}: {}", UID, e.getMessage(), e);
			}
		}

		// 4. Update debounce cache
		if (!normalizedUid.isEmpty()) {
			lastSeenMap.put(normalizedUid, now);
		}
		dispatchedScans.incrementAndGet();

		// Periodic cache pruning if map grows
		if (lastSeenMap.size() > 500) {
			pruneCache(now);
		}
		if (activePresenceMap.size() > 500) {
			prunePresenceCache(now);
		}

		return null;
	}

	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long blockSize) throws Exception {
		return doIt(counter, elements, manufacturerName, tagName, UID, data, blockSize, null);
	}

	@Override
	public void empty() {
		if (hasActiveTags.compareAndSet(true, false)) {
			clearedEvents.incrementAndGet();
			long now = System.currentTimeMillis();
			if (readerId != null && !readerId.isBlank()) {
				logger.info("[READER CLEARED] Reader: {} | All RFID tags removed from reader field", readerId);
			} else {
				logger.info("[READER CLEARED] All RFID tags removed from reader field");
			}
			activePresenceMap.clear();
			lastSeenMap.clear();

			if (wsBrokerDispatcher != null && wsBrokerDispatcher.isActive()) {
				try {
					String clientId = (tagScanProcessor != null && tagScanProcessor.getWebserviceConfig() != null)
							? tagScanProcessor.getWebserviceConfig().getClientId()
							: "";
					String effectiveReaderId = (readerId != null && !readerId.isBlank()) ? readerId : clientId;
					wsBrokerDispatcher.dispatchEmpty(sessionName, now, clientId, effectiveReaderId);
				} catch (Exception e) {
					logger.error("[WSBroker Exception] Failed to dispatch reader cleared event: {}", e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Invoked when the RFID reader disconnects or connection is lost.
	 * Signals that the plate is now empty and dispatches the cleared notification.
	 */
	public void onReaderDisconnected() {
		empty();
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

	private void prunePresenceCache(long now) {
		long thresholdMillis = presenceTtlMillis > 0 ? (presenceTtlMillis * 4) : 5000L;
		long expiryThreshold = now - thresholdMillis;
		activePresenceMap.entrySet().removeIf(entry -> entry.getValue() < expiryThreshold);
	}

	/**
	 * Checks if a specific tag UID is currently physically present on the reader antenna
	 * (last read timestamp is within the active presence TTL window).
	 *
	 * @param uid transponder UID
	 * @return true if the tag is actively present on the antenna, false otherwise
	 */
	public boolean isTagPresent(String uid) {
		if (uid == null || uid.trim().isEmpty()) {
			return false;
		}
		String normalizedUid = uid.trim();
		Long lastSeen = activePresenceMap.get(normalizedUid);
		if (lastSeen == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean present = (now - lastSeen) <= presenceTtlMillis;
		if (!present) {
			activePresenceMap.remove(normalizedUid, lastSeen);
		}
		return present;
	}

	/**
	 * Returns the set of all UIDs currently physically present on the reader antenna.
	 *
	 * @return unmodifiable set of present UIDs
	 */
	public Set<String> getPresentUids() {
		long now = System.currentTimeMillis();
		Set<String> present = new HashSet<>();
		for (Map.Entry<String, Long> entry : activePresenceMap.entrySet()) {
			if ((now - entry.getValue()) <= presenceTtlMillis) {
				present.add(entry.getKey());
			} else {
				activePresenceMap.remove(entry.getKey(), entry.getValue());
			}
		}
		return Collections.unmodifiableSet(present);
	}

	/**
	 * Alias for getPresentUids().
	 */
	public Set<String> getActiveUids() {
		return getPresentUids();
	}

	/**
	 * Returns the count of transponders currently active in the reader field.
	 *
	 * @return number of present tags
	 */
	public int getPresentTagCount() {
		return getPresentUids().size();
	}

	/**
	 * Returns the presence time-to-live window in milliseconds.
	 */
	public long getPresenceTtlMillis() {
		return presenceTtlMillis;
	}

	/**
	 * Clears only the dispatch debounce cooldown cache, keeping active tag presence intact.
	 * Used after write operations so the transponder can be read immediately while remaining
	 * registered as physically present.
	 */
	public void clearDebounceCache() {
		lastSeenMap.clear();
		logger.debug("Cleared scan debounce cache (active tag presence intact)");
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

	public ConcurrentHashMap<String, Long> getActivePresenceMap() {
		return activePresenceMap;
	}

	public void clearCache() {
		lastSeenMap.clear();
		activePresenceMap.clear();
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

	public String getReaderId() {
		return readerId;
	}
}

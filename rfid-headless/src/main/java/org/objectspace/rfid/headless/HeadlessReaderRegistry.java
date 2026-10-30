package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages registered reader instances, fallback reader hardware references,
 * exclusive reader locking, and target reader resolution for incoming WSBroker events.
 */
public class HeadlessReaderRegistry {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderRegistry.class);

	private final Map<String, HeadlessReaderInstance> readerInstances = new ConcurrentHashMap<>();
	private volatile ISO15693Reader reader;
	private volatile HeadlessReaderScanner scanner;
	private volatile HeadlessReaderCallback callback;

	public HeadlessReaderRegistry() {
	}

	/**
	 * Registers a configured reader instance for targeted WSBroker routing and write command execution.
	 */
	public void registerReaderInstance(HeadlessReaderInstance instance) {
		if (instance != null) {
			String id = instance.getReaderId();
			if (id != null && !id.isBlank()) {
				readerInstances.put(id.trim().toLowerCase(), instance);
			}
			if (this.reader == null) {
				this.reader = instance.getReader();
			}
			if (this.scanner == null) {
				this.scanner = instance.getScanner();
			}
			if (this.callback == null) {
				this.callback = instance.getCallback();
			}
		}
	}

	/**
	 * Sets the collection of reader instances managed by this registry.
	 */
	public void setReaderInstances(List<HeadlessReaderInstance> instances) {
		readerInstances.clear();
		if (instances != null) {
			for (HeadlessReaderInstance inst : instances) {
				registerReaderInstance(inst);
			}
		}
	}

	/**
	 * Returns an unmodifiable view of all registered reader instances keyed by lowercase reader ID.
	 */
	public Map<String, HeadlessReaderInstance> getReaderInstances() {
		return Collections.unmodifiableMap(readerInstances);
	}

	/**
	 * Retrieves a specific registered reader instance by reader ID.
	 */
	public HeadlessReaderInstance getReaderInstance(String readerId) {
		if (readerId == null || readerId.isBlank()) {
			return null;
		}
		return readerInstances.get(readerId.trim().toLowerCase());
	}

	/**
	 * Resolves the target reader instance from event target or payload data.
	 */
	public HeadlessReaderInstance resolveTargetReader(Event<?> evt, JsonNode dataNode, String clientName, List<String> channels) {
		String explicitReaderId = WsBrokerPayloadParser.extractRequestedReaderId(dataNode, evt, clientName, channels);

		if (explicitReaderId != null) {
			HeadlessReaderInstance directMatch = readerInstances.get(explicitReaderId.toLowerCase());
			if (directMatch != null) {
				return directMatch;
			}
			if (!readerInstances.isEmpty()) {
				return null;
			}
		}

		if (!readerInstances.isEmpty()) {
			return readerInstances.values().iterator().next();
		}

		if (reader != null || scanner != null || callback != null) {
			HeadlessReaderConfig cfg = new HeadlessReaderConfig(clientName);
			return new HeadlessReaderInstance(cfg, reader, callback, scanner);
		}

		return null;
	}

	public void setReader(ISO15693Reader reader, String clientName) {
		this.reader = reader;
		updateDefaultReaderInstance(clientName);
	}

	public ISO15693Reader getReader() {
		return reader;
	}

	public void setScanner(HeadlessReaderScanner scanner, String clientName) {
		this.scanner = scanner;
		updateDefaultReaderInstance(clientName);
	}

	public HeadlessReaderScanner getScanner() {
		return scanner;
	}

	public void setCallback(HeadlessReaderCallback callback, String clientName) {
		this.callback = callback;
		updateDefaultReaderInstance(clientName);
	}

	public HeadlessReaderCallback getCallback() {
		return callback;
	}

	public void updateDefaultReaderInstance(String clientName) {
		if (reader != null || scanner != null || callback != null) {
			HeadlessReaderConfig cfg = new HeadlessReaderConfig(clientName);
			registerReaderInstance(new HeadlessReaderInstance(cfg, reader, callback, scanner));
		}
	}

	/**
	 * Executes an exclusive task on the RFID reader.
	 * If a scanner instance is present, delegating to scanner.executeExclusive() guarantees that
	 * the scanning loop is safely paused, timing delays are respected, and the tags cleared notification is sent.
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
}

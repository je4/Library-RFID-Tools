package org.objectspace.rfid.headless;

import java.util.Collections;
import java.util.List;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates an active reader instance including its configuration,
 * hardware driver, isolated callback/cache, and background scanner thread.
 */
public class HeadlessReaderInstance implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderInstance.class);

	private final HeadlessReaderConfig config;
	private final ISO15693Reader reader;
	private final HeadlessReaderCallback callback;
	private final HeadlessReaderScanner scanner;

	public HeadlessReaderInstance(HeadlessReaderConfig config, ISO15693Reader reader,
								  HeadlessReaderCallback callback, HeadlessReaderScanner scanner) {
		this.config = config;
		this.reader = reader;
		this.callback = callback;
		this.scanner = scanner;
	}

	public void start() {
		if (scanner != null) {
			logger.info("Starting reader instance [{}]...", getReaderId());
			scanner.start();
		}
	}

	public void stop() {
		logger.info("Stopping reader instance [{}]...", getReaderId());
		if (scanner != null) {
			try {
				scanner.stop();
			} catch (Exception e) {
				logger.warn("Error stopping scanner for reader [{}]: {}", getReaderId(), e.getMessage());
			}
		}
		if (callback != null) {
			try {
				callback.empty();
			} catch (Exception ignored) {}
		}
		if (reader != null) {
			try {
				reader.close();
			} catch (Exception e) {
				logger.warn("Error closing hardware reader [{}]: {}", getReaderId(), e.getMessage());
			}
		}
		logger.info("Reader instance [{}] stopped.", getReaderId());
	}

	public boolean isConnected() {
		return reader != null && reader.isConnected();
	}

	public boolean isReadOnly() {
		return config != null && config.isReadOnly();
	}

	public String getReaderId() {
		if (config != null && config.getReaderId() != null && !config.getReaderId().isBlank()) {
			return config.getReaderId().trim();
		}
		if (callback != null && callback.getReaderId() != null && !callback.getReaderId().isBlank()) {
			return callback.getReaderId().trim();
		}
		return "default";
	}

	public String getDeviceId() {
		if (reader != null && reader.getDeviceId() != null && !reader.getDeviceId().isBlank()) {
			return reader.getDeviceId().trim();
		}
		if (config != null && config.getDeviceId() != null && !config.getDeviceId().isBlank()) {
			return config.getDeviceId().trim();
		}
		return "";
	}

	public String getDeviceType() {
		if (reader != null && reader.getDeviceType() != null && !reader.getDeviceType().isBlank()) {
			return reader.getDeviceType().trim();
		}
		if (config != null && config.getType() != null && !config.getType().isBlank()) {
			return config.getType().trim();
		}
		return "usb";
	}

	public String getDeviceInfo() {
		return reader != null ? reader.getDeviceInfo() : null;
	}

	public String getInitDescription() {
		return reader != null ? reader.getInitDescription() : null;
	}

	public List<String> getStartupLogs() {
		return reader != null ? reader.getStartupLogs() : Collections.emptyList();
	}

	public <T> T executeExclusive(HeadlessReaderScanner.ScannerTask<T> task) throws Exception {
		if (scanner == null) {
			if (reader == null) {
				throw new IllegalStateException("Neither scanner nor reader hardware is initialized for reader " + getReaderId());
			}
			synchronized (reader) {
				Thread.sleep(100);
				T res = task.run(reader);
				Thread.sleep(150);
				if (callback != null) {
					try {
						callback.empty();
					} catch (Exception ignored) {
					}
				}
				return res;
			}
		}
		return scanner.executeExclusive(task);
	}

	public HeadlessReaderConfig getConfig() {
		return config;
	}

	public ISO15693Reader getReader() {
		return reader;
	}

	public HeadlessReaderCallback getCallback() {
		return callback;
	}

	public HeadlessReaderScanner getScanner() {
		return scanner;
	}

	@Override
	public void close() throws Exception {
		stop();
	}
}

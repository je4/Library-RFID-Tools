package org.objectspace.rfid.headless;

import java.io.File;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autonomous background scanning loop for headless inventory operations.
 * Handles reader communication, automatic reconnection, and continuous inventory cycles.
 */
public class HeadlessReaderScanner implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderScanner.class);

	private final ISO15693Reader reader;
	private final HeadlessReaderCallback callback;
	private final int numBlocks;
	private final int sleepInterval;

	private volatile boolean running = false;
	private Thread workerThread;

	public HeadlessReaderScanner(ISO15693Reader reader, HeadlessReaderCallback callback, int numBlocks, int sleepInterval) {
		this.reader = reader;
		this.callback = callback;
		this.numBlocks = numBlocks > 0 ? numBlocks : HeadlessConfigHelper.DEFAULT_NUM_BLOCKS;
		this.sleepInterval = sleepInterval > 0 ? sleepInterval : HeadlessConfigHelper.DEFAULT_SLEEP_INTERVAL_MS;
	}

	public synchronized void start() {
		if (running) {
			logger.warn("Scanner is already running");
			return;
		}
		running = true;
		workerThread = new Thread(this, "RFID-Headless-Scanner");
		workerThread.setDaemon(false);
		workerThread.start();
		logger.info("HeadlessReaderScanner started (interval: {} ms, blocks: {})", sleepInterval, numBlocks);
	}

	public synchronized void stop() {
		if (!running) {
			return;
		}
		logger.info("Stopping HeadlessReaderScanner...");
		running = false;
		if (workerThread != null) {
			workerThread.interrupt();
			try {
				workerThread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			workerThread = null;
		}
		logger.info("HeadlessReaderScanner stopped");
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				// 1. Connection check and auto-recovery
				if (reader != null && !reader.isConnected()) {
					logger.info("Reader not connected. Attempting connection...");
					try {
						reader.connect();
						if (reader.isConnected()) {
							String devInfo = reader.getDeviceInfo();
							logger.info("Successfully connected to RFID Reader: {}", (devInfo != null ? devInfo : "FEIG USB Reader"));
							if (reader.getStartupLogs() != null) {
								for (String logMsg : reader.getStartupLogs()) {
									logger.info("Reader Init: {}", logMsg);
								}
							}
							ensureHostMode();
						} else {
							logger.warn("Reader connection attempt failed. Retrying in 2 seconds...");
							Thread.sleep(2000);
							continue;
						}
					} catch (Exception e) {
						logger.warn("Reader connection error: {}. Retrying in 2 seconds...", e.getMessage());
						Thread.sleep(2000);
						continue;
					}
				}

				// 2. Perform inventory cycle
				if (reader != null && reader.isConnected()) {
					try {
						reader.inventory(callback, numBlocks);
					} catch (Exception e) {
						logger.warn("Inventory read error: {}", e.getMessage());
						if (!reader.checkConnection()) {
							logger.warn("Reader connection lost. Resetting connection...");
							try {
								reader.close();
							} catch (Exception ignored) {}
						}
					}
				}

				// 3. Sleep between scan cycles
				if (sleepInterval > 0) {
					Thread.sleep(sleepInterval);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				logger.error("Unexpected error in scanner loop: {}", e.getMessage(), e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	/**
	 * Checks whether the connected RFID reader is in Host Mode.
	 * If not in Host Mode, writes the Host Mode configuration into the device
	 * without creating a backup, ensuring proper timing for EEPROM writes and system reset.
	 */
	public void ensureHostMode() {
		if (reader == null || !reader.isConnected()) {
			return;
		}
		try {
			logger.info("Checking reader operating mode...");
			if (!reader.isHostMode()) {
				logger.warn("FEIG reader is not in Host Mode! Configuring Host Mode now...");
				File hostConfigFile = reader.resolveHostModeConfigFile();
				if (hostConfigFile == null || !hostConfigFile.exists()) {
					hostConfigFile = FeigRFID.resolveHostModeConfigFile(null);
				}
				if (hostConfigFile == null || !hostConfigFile.exists()) {
					logger.error("Host Mode configuration file 'reader_host_mode_config.xml' could not be resolved! Cannot configure reader.");
					return;
				}
				// Brief delay to allow initial connection / in-flight reader operations to settle
				Thread.sleep(400);
				logger.info("Writing Host Mode configuration ({}) to reader EEPROM & RAM...", hostConfigFile.getName());
				reader.restoreHostModeConfig(hostConfigFile);
				// Additional delay to ensure reader reboot and initialization completes cleanly
				Thread.sleep(500);
				logger.info("FEIG reader successfully configured in Host Mode and reinitialized.");
			} else {
				logger.info("FEIG reader is confirmed in Host Mode.");
			}
		} catch (Exception e) {
			logger.error("Failed to check or apply Host Mode configuration on reader: {}", e.getMessage(), e);
		}
	}

	public boolean isRunning() {
		return running;
	}

	public ISO15693Reader getReader() {
		return reader;
	}

	public HeadlessReaderCallback getCallback() {
		return callback;
	}
}

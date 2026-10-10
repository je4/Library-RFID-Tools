package org.objectspace.rfid.raspi;

import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autonomous background scanning loop for headless inventory operations.
 * Handles reader communication, automatic reconnection, and continuous inventory cycles.
 */
public class RaspiInventoryScanner implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(RaspiInventoryScanner.class);

	private final ISO15693Reader reader;
	private final RaspiInventoryCallback callback;
	private final int numBlocks;
	private final int sleepInterval;

	private volatile boolean running = false;
	private Thread workerThread;

	public RaspiInventoryScanner(ISO15693Reader reader, RaspiInventoryCallback callback, int numBlocks, int sleepInterval) {
		this.reader = reader;
		this.callback = callback;
		this.numBlocks = numBlocks > 0 ? numBlocks : RaspiConfigHelper.DEFAULT_NUM_BLOCKS;
		this.sleepInterval = sleepInterval > 0 ? sleepInterval : RaspiConfigHelper.DEFAULT_SLEEP_INTERVAL_MS;
	}

	public synchronized void start() {
		if (running) {
			logger.warn("Scanner is already running");
			return;
		}
		running = true;
		workerThread = new Thread(this, "Raspi-Inventory-Scanner");
		workerThread.setDaemon(false);
		workerThread.start();
		logger.info("RaspiInventoryScanner started (interval: {} ms, blocks: {})", sleepInterval, numBlocks);
	}

	public synchronized void stop() {
		if (!running) {
			return;
		}
		logger.info("Stopping RaspiInventoryScanner...");
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
		logger.info("RaspiInventoryScanner stopped");
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

	public boolean isRunning() {
		return running;
	}

	public ISO15693Reader getReader() {
		return reader;
	}

	public RaspiInventoryCallback getCallback() {
		return callback;
	}
}

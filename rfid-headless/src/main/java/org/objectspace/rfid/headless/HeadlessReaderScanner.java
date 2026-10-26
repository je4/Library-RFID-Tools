package org.objectspace.rfid.headless;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autonomous background scanning loop for headless inventory operations.
 * Handles reader communication, automatic reconnection, continuous inventory cycles,
 * and exclusive write locking with pause/resume synchronization.
 */
public class HeadlessReaderScanner implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReaderScanner.class);

	private final ISO15693Reader reader;
	private final HeadlessReaderCallback callback;
	private final int numBlocks;
	private final int sleepInterval;
	private final String readerId;

	private final ReentrantLock scanLock = new ReentrantLock(true);
	private final AtomicBoolean paused = new AtomicBoolean(false);

	private volatile boolean running = false;
	private Thread workerThread;

	public HeadlessReaderScanner(ISO15693Reader reader, HeadlessReaderCallback callback, int numBlocks, int sleepInterval) {
		this(reader, callback, numBlocks, sleepInterval, callback != null ? callback.getReaderId() : null);
	}

	public HeadlessReaderScanner(ISO15693Reader reader, HeadlessReaderCallback callback, int numBlocks, int sleepInterval, String readerId) {
		this.reader = reader;
		this.callback = callback;
		this.numBlocks = numBlocks > 0 ? numBlocks : HeadlessConfigHelper.DEFAULT_NUM_BLOCKS;
		this.sleepInterval = sleepInterval > 0 ? sleepInterval : HeadlessConfigHelper.DEFAULT_SLEEP_INTERVAL_MS;
		this.readerId = readerId != null && !readerId.isBlank() ? readerId.trim() : (callback != null ? callback.getReaderId() : null);
	}

	public synchronized void start() {
		if (running) {
			logger.warn("Scanner is already running{}", (readerId != null ? " for reader: " + readerId : ""));
			return;
		}
		running = true;
		String threadName = (readerId != null && !readerId.isBlank()) ? "RFID-Scanner-" + readerId : "RFID-Headless-Scanner";
		workerThread = new Thread(this, threadName);
		workerThread.setDaemon(false);
		workerThread.start();
		logger.info("HeadlessReaderScanner started [thread: {}] (interval: {} ms, blocks: {})",
				threadName, sleepInterval, numBlocks);
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
		if (callback != null) {
			try {
				callback.empty();
			} catch (Exception e) {
				logger.warn("Error invoking callback.empty() during scanner stop: {}", e.getMessage());
			}
		}
		logger.info("HeadlessReaderScanner stopped");
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				if (paused.get()) {
					Thread.sleep(50);
					continue;
				}

				scanLock.lock();
				try {
					if (paused.get()) {
						continue;
					}

					// 1. Connection check and auto-recovery
					if (reader != null && !reader.isConnected()) {
						if (callback != null) {
							callback.empty();
						}
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
								logger.warn("Reader connection lost. Resetting connection and clearing active plate tags...");
								try {
									reader.close();
								} catch (Exception ignored) {}
								if (callback != null) {
									callback.empty();
								}
							}
						}
					}
				} finally {
					scanLock.unlock();
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
	 * Functional interface for reader operations executed exclusively while the scan loop is paused.
	 */
	@FunctionalInterface
	public interface ScannerTask<T> {
		T run(ISO15693Reader reader) throws Exception;
	}

	/**
	 * Pauses the background scanning loop.
	 */
	public void pause() {
		paused.set(true);
		logger.debug("HeadlessReaderScanner paused");
	}

	/**
	 * Resumes the background scanning loop.
	 */
	public void resume() {
		paused.set(false);
		logger.debug("HeadlessReaderScanner resumed");
	}

	/**
	 * Checks whether the scanner loop is currently paused.
	 */
	public boolean isPaused() {
		return paused.get();
	}

	/**
	 * Executes an exclusive task on the RFID reader while pausing the scanning loop.
	 * Guarantees proper timing:
	 * 1. Pauses the scanning loop and waits for any active scan cycle to finish.
	 * 2. Allows a brief settling delay before writing to the transponder.
	 * 3. Executes the reader task (e.g. write tag blocks or AFI).
	 * 4. Waits for EEPROM / transponder write completion.
	 * 5. Dispatches a tags cleared notification and clears the cache so the written tag is scanned cleanly.
	 * 6. Resumes the scanning loop in all cases (including exceptions).
	 *
	 * @param task task to execute with exclusive reader access
	 * @param <T>  return type of task
	 * @return task result
	 * @throws Exception if task execution fails
	 */
	public <T> T executeExclusive(ScannerTask<T> task) throws Exception {
		paused.set(true);
		scanLock.lock();
		try {
			logger.info("Acquired exclusive reader lock for write operation. Pausing scanner loop...");
			// Brief delay to ensure any in-flight RF operations settle
			Thread.sleep(100);
			T result = task.run(reader);
			// Transponder / EEPROM write settling delay
			Thread.sleep(150);
			if (callback != null) {
				try {
					callback.empty();
				} catch (Exception e) {
					logger.warn("Error invoking callback.empty() after write operation: {}", e.getMessage());
				}
				logger.info("Dispatched tags cleared message after write operation");
			}
			return result;
		} finally {
			paused.set(false);
			scanLock.unlock();
			logger.info("Released exclusive reader lock. Scanning loop resumed.");
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

	public String getReaderId() {
		return readerId;
	}

	public Thread getWorkerThread() {
		return workerThread;
	}
}

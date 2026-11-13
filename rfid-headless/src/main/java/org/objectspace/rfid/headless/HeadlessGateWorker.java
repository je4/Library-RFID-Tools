package org.objectspace.rfid.headless;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.feig.ISO15693Feig;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autonomous background worker for FEIG Buffered Reader Mode gates.
 * Runs on a dedicated single worker thread to maintain strict SDK thread affinity.
 * Periodically polls the reader buffer for transponder passage events,
 * polls people counter and diagnostic status, and executes tasks exclusively on the worker thread.
 */
public class HeadlessGateWorker implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessGateWorker.class);

	private final ISO15693Reader reader;
	private final HeadlessReaderCallback callback;
	private final HeadlessWsBrokerDispatcher wsBrokerDispatcher;
	private final int bufferPollIntervalMs;
	private final int gatePollIntervalMs;
	private final boolean gateStatusEnabled;
	private final String readerId;
	private final boolean autoConfig;
	private final File bufferedModeConfigFile;

	private final BlockingQueue<FutureTask<?>> taskQueue = new LinkedBlockingQueue<>();
	private final AtomicBoolean running = new AtomicBoolean(false);

	private Thread workerThread;
	private long lastGatePollTime = 0L;
	private boolean autoConfigApplied;

	public interface BufferedTagReader {
		List<FeigRFID.BufferedTag> readBufferedTags(int maxCount) throws Exception;
	}

	public interface GateStatusReader {
		List<FeigRFID.GateCounter> readGateCounters() throws Exception;
		List<FeigRFID.GateDiagnostic> readGateDiagnostics() throws Exception;
	}

	public interface BufferedModeConfigurator {
		void configureBufferedMode(File configFile) throws Exception;
	}

	public HeadlessGateWorker(ISO15693Reader reader,
							  HeadlessReaderCallback callback,
							  HeadlessWsBrokerDispatcher wsBrokerDispatcher,
							  int bufferPollIntervalMs,
							  int gatePollIntervalMs,
							  boolean gateStatusEnabled,
							  String readerId) {
		this(reader, callback, wsBrokerDispatcher, bufferPollIntervalMs, gatePollIntervalMs,
				gateStatusEnabled, readerId, false, null);
	}

	public HeadlessGateWorker(ISO15693Reader reader,
							  HeadlessReaderCallback callback,
							  HeadlessWsBrokerDispatcher wsBrokerDispatcher,
							  int bufferPollIntervalMs,
							  int gatePollIntervalMs,
							  boolean gateStatusEnabled,
							  String readerId,
							  boolean autoConfig,
							  File bufferedModeConfigFile) {
		this.reader = reader;
		this.callback = callback;
		this.wsBrokerDispatcher = wsBrokerDispatcher;
		this.bufferPollIntervalMs = bufferPollIntervalMs > 0 ? bufferPollIntervalMs : 300;
		this.gatePollIntervalMs = gatePollIntervalMs > 0 ? gatePollIntervalMs : 30000;
		this.gateStatusEnabled = gateStatusEnabled;
		this.readerId = (readerId != null && !readerId.isBlank()) ? readerId.trim() : "default";
		this.autoConfig = autoConfig;
		this.bufferedModeConfigFile = bufferedModeConfigFile;
	}

	public synchronized void start() {
		if (running.get()) {
			logger.warn("Gate worker is already running for reader [{}]", readerId);
			return;
		}
		running.set(true);
		String threadName = "RFID-GateWorker-" + readerId;
		workerThread = new Thread(this, threadName);
		workerThread.setDaemon(false);
		workerThread.start();
		logger.info("HeadlessGateWorker started [thread: {}] (bufferPoll: {} ms, gatePoll: {} ms, gateStatus: {})",
				threadName, bufferPollIntervalMs, gatePollIntervalMs, gateStatusEnabled);
	}

	public synchronized void stop() {
		if (!running.get()) {
			return;
		}
		logger.info("Stopping HeadlessGateWorker for reader [{}]...", readerId);
		running.set(false);
		if (workerThread != null) {
			workerThread.interrupt();
			try {
				workerThread.join(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			workerThread = null;
		}
		logger.info("HeadlessGateWorker stopped for reader [{}]", readerId);
	}

	public boolean isRunning() {
		return running.get();
	}

	public Thread getWorkerThread() {
		return workerThread;
	}

	public String getReaderId() {
		return readerId;
	}

	public <T> T executeExclusive(HeadlessReaderScanner.ScannerTask<T> task) throws Exception {
		if (!running.get() && (workerThread == null || !workerThread.isAlive())) {
			if (reader == null) {
				throw new IllegalStateException("Reader hardware is not initialized for gate " + readerId);
			}
			return task.run(reader);
		}
		if (Thread.currentThread() == workerThread) {
			return task.run(reader);
		}
		FutureTask<T> future = new FutureTask<>(() -> task.run(reader));
		taskQueue.put(future);
		try {
			return future.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception ex) {
				throw ex;
			}
			throw new RuntimeException(cause);
		}
	}

	@Override
	public void run() {
		logger.info("HeadlessGateWorker running on dedicated thread [{}] for reader [{}]",
				Thread.currentThread().getName(), readerId);

		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				processPendingTasks();

				if (reader != null && !reader.isConnected()) {
					try {
						logger.info("Connecting gate reader [{}] on worker thread...", readerId);
						reader.connect();
					} catch (Exception e) {
						logger.warn("Gate reader [{}] connection failed: {}. Retrying in 5s...", readerId, e.getMessage());
						long sleepUntil = System.currentTimeMillis() + 5000L;
						while (running.get() && System.currentTimeMillis() < sleepUntil && !Thread.currentThread().isInterrupted()) {
							FutureTask<?> pending = taskQueue.poll(200, TimeUnit.MILLISECONDS);
							if (pending != null) {
								pending.run();
							}
						}
						continue;
					}
					if (reader.isConnected()) {
						logger.info("Gate reader [{}] successfully connected.", readerId);
					}
				}

				if (reader != null && reader.isConnected() && autoConfig && !autoConfigApplied) {
					try {
						applyBufferedModeConfiguration();
					} catch (Exception e) {
						logger.error("Buffered Mode auto-configuration failed for reader [{}]; stopping gate worker: {}",
								readerId, e.getMessage(), e);
						running.set(false);
						break;
					}
				}

				pollBufferedTags();

				if (gateStatusEnabled) {
					pollGateStatusIfDue();
				}

				processPendingTasks();

				long sleepMs = Math.max(50, bufferPollIntervalMs);
				FutureTask<?> nextTask = taskQueue.poll(sleepMs, TimeUnit.MILLISECONDS);
				if (nextTask != null) {
					nextTask.run();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				logger.warn("Error during gate worker cycle for reader [{}]: {}", readerId, e.getMessage());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		if (reader != null && reader.isConnected()) {
			try {
				logger.info("Closing gate reader [{}] on worker thread...", readerId);
				reader.close();
			} catch (Exception e) {
				logger.warn("Error closing gate reader [{}] on worker thread: {}", readerId, e.getMessage());
			}
		}
		logger.info("HeadlessGateWorker thread loop ended for reader [{}]", readerId);
	}

	private void applyBufferedModeConfiguration() throws Exception {
		File configFile = bufferedModeConfigFile != null
				? bufferedModeConfigFile
				: FeigRFID.resolveBufferedModeConfigFile(null);
		if (configFile == null || !configFile.exists() || !configFile.isFile()) {
			throw new IllegalStateException("Buffered Mode configuration file could not be resolved");
		}
		if (reader instanceof ISO15693Feig feigReader) {
			FeigRFID feig = feigReader.getFeigRFID();
			if (feig == null) {
				throw new IllegalStateException("FEIG reader is not initialized for Buffered Mode auto-configuration");
			}
			feig.configureBufferedMode(configFile);
		} else if (reader instanceof BufferedModeConfigurator configurator) {
			configurator.configureBufferedMode(configFile);
		} else {
			throw new IllegalStateException("Buffered Mode auto-configuration requires a FEIG reader");
		}
		autoConfigApplied = true;
		logger.info("Buffered Mode configuration [{}] applied to gate reader [{}]", configFile.getName(), readerId);
	}

	private void processPendingTasks() {
		FutureTask<?> task;
		while ((task = taskQueue.poll()) != null) {
			task.run();
		}
	}

	private void pollBufferedTags() {
		if (reader == null || !reader.isConnected()) {
			return;
		}
		try {
			List<FeigRFID.BufferedTag> tags = null;
			if (reader instanceof ISO15693Feig feigReader) {
				tags = feigReader.readBufferedTags(100);
			} else if (reader instanceof BufferedTagReader customReader) {
				tags = customReader.readBufferedTags(100);
			}
			if (tags != null && !tags.isEmpty()) {
				logger.info("Gate [{}] retrieved {} buffered tag event(s)", readerId, tags.size());
				for (FeigRFID.BufferedTag tag : tags) {
					processBufferedTag(tag);
				}
			}
		} catch (Exception e) {
			logger.warn("Gate [{}] buffer polling failed: {}", readerId, e.getMessage());
		}
	}

	private void processBufferedTag(FeigRFID.BufferedTag tag) {
		if (tag == null || tag.uid() == null || tag.uid().isBlank()) {
			return;
		}
		try {
			if (callback != null) {
				callback.doIt(1, 1, "FEIG", "ISO15693", tag.uid(), tag.data(), tag.blockSize(), null, tag.dateTime());
			}
		} catch (Exception e) {
			logger.error("Error processing buffered tag {} on reader [{}]: {}", tag.uid(), readerId, e.getMessage(), e);
		}
	}

	private void pollGateStatusIfDue() {
		long now = System.currentTimeMillis();
		if (now - lastGatePollTime < gatePollIntervalMs) {
			return;
		}
		lastGatePollTime = now;
		try {
			List<FeigRFID.GateCounter> counters = null;
			List<FeigRFID.GateDiagnostic> diagnostics = null;
			if (reader instanceof ISO15693Feig feigReader) {
				counters = feigReader.readGateCounters();
				diagnostics = feigReader.readGateDiagnostics();
			} else if (reader instanceof GateStatusReader statusReader) {
				counters = statusReader.readGateCounters();
				diagnostics = statusReader.readGateDiagnostics();
			}
			if (wsBrokerDispatcher != null && wsBrokerDispatcher.isActive()) {
				Map<String, Object> data = Map.of(
						"readerId", readerId,
						"counters", (counters != null ? counters : List.of()),
						"diagnostics", (diagnostics != null ? diagnostics : List.of())
				);
				wsBrokerDispatcher.dispatch("rfid.gate.status", data, null, null, now, readerId);
				logger.debug("Gate [{}] published status: {} counter(s), {} diagnostic(s)",
						readerId, (counters != null ? counters.size() : 0), (diagnostics != null ? diagnostics.size() : 0));
			}
		} catch (Exception e) {
			logger.warn("Gate [{}] status polling failed: {}", readerId, e.getMessage());
		}
	}
}

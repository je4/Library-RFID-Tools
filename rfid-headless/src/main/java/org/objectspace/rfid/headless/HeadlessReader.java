package org.objectspace.rfid.headless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.configuration2.XMLConfiguration;
import org.objectspace.rfid.ISO15693ReaderFactory;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point and CLI daemon for rfid-headless on Raspberry Pi (aarch64) and Linux systems.
 * Launches continuous RFID scanning and webservice dispatching across one or multiple reader instances
 * without requiring a graphical interface.
 */
public class HeadlessReader {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReader.class);
	public static final String VERSION = "2.0.0";

	private final XMLConfiguration config;
	private final TagScanProcessor tagScanProcessor;
	private final HeadlessWsBrokerDispatcher wsBrokerDispatcher;
	private final List<HeadlessReaderInstance> readerInstances;

	public HeadlessReader(XMLConfiguration config, Long debounceOverride, Integer sleepOverride, Integer blocksOverride) throws Exception {
		this.config = config;
		this.tagScanProcessor = new TagScanProcessor(config);
		this.wsBrokerDispatcher = HeadlessWsBrokerDispatcher.fromConfiguration(config);

		List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
		this.readerInstances = new ArrayList<>();

		for (HeadlessReaderConfig rCfg : readerConfigs) {
			if (debounceOverride != null && debounceOverride >= 0) {
				rCfg.setDebounceMillis(debounceOverride);
			}
			if (sleepOverride != null && sleepOverride > 0) {
				rCfg.setSleepInterval(sleepOverride);
			}
			if (blocksOverride != null && blocksOverride > 0) {
				rCfg.setNumBlocks(blocksOverride);
			}

			ISO15693Reader reader = ISO15693ReaderFactory.createReader(rCfg.toConfiguration());
			HeadlessReaderCallback callback = new HeadlessReaderCallback(
					tagScanProcessor, wsBrokerDispatcher, rCfg.getDebounceMillis(), rCfg.getReaderId());
			HeadlessReaderScanner scanner = new HeadlessReaderScanner(
					reader, callback, rCfg.getNumBlocks(), rCfg.getSleepInterval(), rCfg.getReaderId());

			HeadlessReaderInstance instance = new HeadlessReaderInstance(rCfg, reader, callback, scanner);
			this.readerInstances.add(instance);
		}

		if (this.wsBrokerDispatcher != null) {
			this.wsBrokerDispatcher.setReaderInstances(this.readerInstances);
		}
	}

	public void start() {
		logger.info("==================================================");
		logger.info(" Starting RFID-Headless Reader Daemon v{} (Headless) ", VERSION);
		logger.info(" Managing {} configured RFID reader instance(s)", readerInstances.size());
		logger.info("==================================================");

		// Connect to WSBroker if configured
		if (wsBrokerDispatcher != null && wsBrokerDispatcher.isActive()) {
			wsBrokerDispatcher.connect();
		}

		// Register graceful shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("Shutdown signal received (SIGINT/SIGTERM). Cleaning up resources...");
			for (HeadlessReaderInstance instance : readerInstances) {
				try {
					instance.stop();
				} catch (Exception e) {
					logger.warn("Error stopping reader instance [{}]: {}", instance.getReaderId(), e.getMessage());
				}
			}
			try {
				if (wsBrokerDispatcher != null) {
					wsBrokerDispatcher.close();
				}
			} catch (Exception e) {
				logger.warn("Error closing WSBroker dispatcher: {}", e.getMessage());
			}
			try {
				tagScanProcessor.close();
			} catch (Exception e) {
				logger.warn("Error closing tag scan processor: {}", e.getMessage());
			}
			logger.info("RFID-Headless Reader shutdown complete.");
		}, "RFID-Headless-Shutdown-Hook"));

		for (HeadlessReaderInstance instance : readerInstances) {
			instance.start();
		}
	}

	public boolean isRunning() {
		for (HeadlessReaderInstance instance : readerInstances) {
			if (instance.getScanner() != null && instance.getScanner().isRunning()) {
				return true;
			}
		}
		return false;
	}

	public List<HeadlessReaderInstance> getReaderInstances() {
		return Collections.unmodifiableList(readerInstances);
	}

	public HeadlessReaderInstance getReaderInstance(String readerId) {
		if (readerId == null || readerId.isBlank()) {
			return null;
		}
		for (HeadlessReaderInstance inst : readerInstances) {
			if (readerId.equalsIgnoreCase(inst.getReaderId())) {
				return inst;
			}
		}
		return null;
	}

	public HeadlessReaderScanner getScanner() {
		return !readerInstances.isEmpty() ? readerInstances.get(0).getScanner() : null;
	}

	public HeadlessReaderCallback getCallback() {
		return !readerInstances.isEmpty() ? readerInstances.get(0).getCallback() : null;
	}

	public TagScanProcessor getTagScanProcessor() {
		return tagScanProcessor;
	}

	public HeadlessWsBrokerDispatcher getWsBrokerDispatcher() {
		return wsBrokerDispatcher;
	}

	public ISO15693Reader getReader() {
		return !readerInstances.isEmpty() ? readerInstances.get(0).getReader() : null;
	}

	public XMLConfiguration getConfig() {
		return config;
	}

	public static void main(String[] args) {
		String configFile = null;
		Long debounceOverride = null;
		Integer sleepOverride = null;
		Integer blocksOverride = null;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("-h".equals(arg) || "--help".equals(arg)) {
				printHelp();
				System.exit(0);
			} else if ("-v".equals(arg) || "--version".equals(arg)) {
				System.out.println("rfid-headless version " + VERSION);
				System.exit(0);
			} else if ("--debug".equals(arg)) {
				setLogLevel(ch.qos.logback.classic.Level.DEBUG);
			} else if (("-c".equals(arg) || "--config".equals(arg)) && i + 1 < args.length) {
				configFile = args[++i];
			} else if (("-d".equals(arg) || "--debounce".equals(arg)) && i + 1 < args.length) {
				debounceOverride = Long.parseLong(args[++i]);
			} else if (("-s".equals(arg) || "--sleep".equals(arg)) && i + 1 < args.length) {
				sleepOverride = Integer.parseInt(args[++i]);
			} else if (("-b".equals(arg) || "--blocks".equals(arg)) && i + 1 < args.length) {
				blocksOverride = Integer.parseInt(args[++i]);
			} else if (configFile == null && !arg.startsWith("-")) {
				configFile = arg;
			}
		}

		try {
			XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(configFile);
			HeadlessReader app = new HeadlessReader(config, debounceOverride, sleepOverride, blocksOverride);
			app.start();

			// Keep main thread alive
			while (app.isRunning()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		} catch (Exception e) {
			logger.error("Fatal startup error in HeadlessReader: {}", e.getMessage(), e);
			System.exit(1);
		}
	}

	public static void setLogLevel(ch.qos.logback.classic.Level level) {
		try {
			ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			root.setLevel(level);
		} catch (Exception e) {
			logger.warn("Could not set log level dynamically: {}", e.getMessage());
		}
	}

	private static void printHelp() {
		System.out.println("Usage: java -jar rfid-headless.jar [options] [config-file]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  -c, --config <file>     Path to inventory.xml configuration file (default: inventory.xml)");
		System.out.println("  -d, --debounce <ms>     Debounce window in ms to suppress duplicate reads (default: 3000)");
		System.out.println("  -s, --sleep <ms>        Sleep interval between scan cycles in ms (default: 300)");
		System.out.println("  -b, --blocks <num>      Number of blocks to read from ISO 15693 tag (default: 12)");
		System.out.println("  --debug                 Enable DEBUG logging output");
		System.out.println("  -v, --version           Print version information and exit");
		System.out.println("  -h, --help              Print this help message and exit");
	}
}

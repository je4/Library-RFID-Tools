package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.XMLConfiguration;
import org.objectspace.rfid.ISO15693ReaderFactory;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point and CLI daemon for rfid-headless on Raspberry Pi (aarch64) and Linux systems.
 * Launches continuous RFID scanning and webservice dispatching without a graphical interface.
 */
public class HeadlessReader {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessReader.class);
	public static final String VERSION = "2.0.0";

	private final XMLConfiguration config;
	private final TagScanProcessor tagScanProcessor;
	private final ISO15693Reader reader;
	private final HeadlessReaderCallback callback;
	private final HeadlessReaderScanner scanner;

	public HeadlessReader(XMLConfiguration config, Long debounceOverride, Integer sleepOverride, Integer blocksOverride) throws Exception {
		this.config = config;
		this.tagScanProcessor = new TagScanProcessor(config);
		this.reader = ISO15693ReaderFactory.createReader(config);

		long debounce = (debounceOverride != null && debounceOverride > 0)
				? debounceOverride
				: HeadlessConfigHelper.getDebounceMillis(config);

		int sleepInterval = (sleepOverride != null && sleepOverride > 0)
				? sleepOverride
				: HeadlessConfigHelper.getSleepInterval(config);

		int numBlocks = (blocksOverride != null && blocksOverride > 0)
				? blocksOverride
				: HeadlessConfigHelper.getNumBlocks(config);

		this.callback = new HeadlessReaderCallback(tagScanProcessor, debounce);
		this.scanner = new HeadlessReaderScanner(reader, callback, numBlocks, sleepInterval);
	}

	public void start() {
		logger.info("==================================================");
		logger.info(" Starting RFID-Headless Reader Daemon v{} (Headless) ", VERSION);
		logger.info("==================================================");

		// Register graceful shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("Shutdown signal received (SIGINT/SIGTERM). Cleaning up resources...");
			try {
				scanner.stop();
			} catch (Exception e) {
				logger.warn("Error stopping scanner: {}", e.getMessage());
			}
			try {
				if (reader != null && reader.isConnected()) {
					reader.close();
					logger.info("RFID Reader closed/disconnected");
				}
			} catch (Exception e) {
				logger.warn("Error disconnecting reader: {}", e.getMessage());
			}
			try {
				tagScanProcessor.close();
			} catch (Exception e) {
				logger.warn("Error closing tag scan processor: {}", e.getMessage());
			}
			logger.info("RFID-Headless Reader shutdown complete.");
		}, "RFID-Headless-Shutdown-Hook"));

		scanner.start();
	}

	public HeadlessReaderScanner getScanner() {
		return scanner;
	}

	public HeadlessReaderCallback getCallback() {
		return callback;
	}

	public TagScanProcessor getTagScanProcessor() {
		return tagScanProcessor;
	}

	public ISO15693Reader getReader() {
		return reader;
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
			while (app.getScanner().isRunning()) {
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

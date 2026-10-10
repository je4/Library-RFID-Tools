package org.objectspace.rfid.raspi;

import org.apache.commons.configuration2.XMLConfiguration;
import org.objectspace.rfid.ISO15693ReaderFactory;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.library.ISO15693Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point and CLI daemon for raspi-inventory on Raspberry Pi (aarch64).
 * Launches continuous RFID scanning without a graphical interface.
 */
public class RaspiInventory {

	private static final Logger logger = LoggerFactory.getLogger(RaspiInventory.class);
	public static final String VERSION = "2.0.0";

	private final XMLConfiguration config;
	private final TagScanProcessor tagScanProcessor;
	private final ISO15693Reader reader;
	private final RaspiInventoryCallback callback;
	private final RaspiInventoryScanner scanner;

	public RaspiInventory(XMLConfiguration config, Long debounceOverride, Integer sleepOverride, Integer blocksOverride) throws Exception {
		this.config = config;
		this.tagScanProcessor = new TagScanProcessor(config);
		this.reader = ISO15693ReaderFactory.createReader(config);

		long debounce = (debounceOverride != null && debounceOverride > 0)
				? debounceOverride
				: RaspiConfigHelper.getDebounceMillis(config);

		int sleepInterval = (sleepOverride != null && sleepOverride > 0)
				? sleepOverride
				: RaspiConfigHelper.getSleepInterval(config);

		int numBlocks = (blocksOverride != null && blocksOverride > 0)
				? blocksOverride
				: RaspiConfigHelper.getNumBlocks(config);

		this.callback = new RaspiInventoryCallback(tagScanProcessor, debounce);
		this.scanner = new RaspiInventoryScanner(reader, callback, numBlocks, sleepInterval);
	}

	public void start() {
		logger.info("==================================================");
		logger.info(" Starting Raspi-Inventory Daemon v{} (Headless) ", VERSION);
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
			logger.info("Raspi-Inventory shutdown complete.");
		}, "Raspi-Shutdown-Hook"));

		scanner.start();
	}

	public RaspiInventoryScanner getScanner() {
		return scanner;
	}

	public RaspiInventoryCallback getCallback() {
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
				System.out.println("raspi-inventory version " + VERSION);
				System.exit(0);
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
			XMLConfiguration config = RaspiConfigHelper.loadConfiguration(configFile);
			RaspiInventory app = new RaspiInventory(config, debounceOverride, sleepOverride, blocksOverride);
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
			logger.error("Fatal startup error in RaspiInventory: {}", e.getMessage(), e);
			System.exit(1);
		}
	}

	private static void printHelp() {
		System.out.println("Usage: java -jar raspi-inventory.jar [options] [config-file]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  -c, --config <file>     Path to inventory.xml configuration file (default: inventory.xml)");
		System.out.println("  -d, --debounce <ms>     Debounce window in ms to suppress duplicate reads (default: 3000)");
		System.out.println("  -s, --sleep <ms>        Sleep interval between scan cycles in ms (default: 300)");
		System.out.println("  -b, --blocks <num>      Number of blocks to read from ISO 15693 tag (default: 12)");
		System.out.println("  -v, --version           Print version information and exit");
		System.out.println("  -h, --help              Print this help message and exit");
	}
}

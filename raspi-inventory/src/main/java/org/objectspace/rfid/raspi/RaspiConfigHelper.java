package org.objectspace.rfid.raspi;

import java.io.File;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for loading and accessing configuration for raspi-inventory.
 * Gracefully ignores GUI-specific elements in inventory.xml.
 */
public class RaspiConfigHelper {

	private static final Logger logger = LoggerFactory.getLogger(RaspiConfigHelper.class);

	public static final String DEFAULT_CONFIG_FILE = "inventory.xml";
	public static final int DEFAULT_SLEEP_INTERVAL_MS = 300;
	public static final int DEFAULT_NUM_BLOCKS = 12;
	public static final long DEFAULT_DEBOUNCE_MILLIS = 3000L;

	/**
	 * Loads configuration from the given file path or default inventory.xml.
	 */
	public static XMLConfiguration loadConfiguration(String configFilePath) throws ConfigurationException {
		String targetPath = configFilePath;
		if (targetPath == null || targetPath.trim().isEmpty()) {
			targetPath = System.getProperty("config.file", DEFAULT_CONFIG_FILE);
		}

		File file = new File(targetPath);
		if (!file.exists()) {
			if (DEFAULT_CONFIG_FILE.equals(targetPath)) {
				File raspiFile = new File("raspi-inventory", DEFAULT_CONFIG_FILE);
				File rfidFile = new File("rfid-inventory", DEFAULT_CONFIG_FILE);
				if (raspiFile.exists()) {
					file = raspiFile;
					targetPath = raspiFile.getPath();
				} else if (rfidFile.exists()) {
					file = rfidFile;
					targetPath = rfidFile.getPath();
				}
			}
		}

		if (!file.exists()) {
			logger.warn("Configuration file '{}' not found, falling back to default XML configuration.", targetPath);
		} else {
			logger.info("Loading configuration from '{}'", file.getAbsolutePath());
		}

		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder =
				new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
						.configure(params.xml().setFileName(targetPath));

		XMLConfiguration config = builder.getConfiguration();
		config.setProperty("config.file.path", file.getAbsolutePath());
		return config;
	}

	public static int getSleepInterval(AbstractConfiguration config) {
		if (config == null) {
			return DEFAULT_SLEEP_INTERVAL_MS;
		}
		return config.getInt("inventory.sleep", config.getInt("sleep", DEFAULT_SLEEP_INTERVAL_MS));
	}

	public static int getNumBlocks(AbstractConfiguration config) {
		if (config == null) {
			return DEFAULT_NUM_BLOCKS;
		}
		return config.getInt("numblocks", DEFAULT_NUM_BLOCKS);
	}

	public static long getDebounceMillis(AbstractConfiguration config) {
		if (config == null) {
			return DEFAULT_DEBOUNCE_MILLIS;
		}
		return config.getLong("debounce", DEFAULT_DEBOUNCE_MILLIS);
	}
}

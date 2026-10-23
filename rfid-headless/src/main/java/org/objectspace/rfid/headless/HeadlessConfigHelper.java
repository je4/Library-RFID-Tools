package org.objectspace.rfid.headless;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.XmlConfigParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for loading and accessing configuration for rfid-headless.
 * Gracefully ignores GUI-specific elements in inventory.xml.
 */
public class HeadlessConfigHelper {

	private static final Logger logger = LoggerFactory.getLogger(HeadlessConfigHelper.class);

	public static final String DEFAULT_CONFIG_FILE = "inventory.xml";
	public static final int DEFAULT_SLEEP_INTERVAL_MS = 300;
	public static final int DEFAULT_NUM_BLOCKS = 12;
	public static final long DEFAULT_DEBOUNCE_MILLIS = 3000L;
	public static final String DEFAULT_CLIENT_NAME = "test-java";

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
				File headlessFile = new File("rfid-headless", DEFAULT_CONFIG_FILE);
				File rfidFile = new File("rfid-inventory", DEFAULT_CONFIG_FILE);
				if (headlessFile.exists()) {
					file = headlessFile;
					targetPath = headlessFile.getPath();
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
		String[] keys = new String[] {
				"debounce",
				"inventory.debounce",
				"resend_interval",
				"resend_time",
				"tag_resend_time",
				"cooldown"
		};
		for (String key : keys) {
			if (config.containsKey(key)) {
				long val = config.getLong(key, -1L);
				if (val >= 0) {
					return val;
				}
			}
		}
		return DEFAULT_DEBOUNCE_MILLIS;
	}

	public static boolean isWsBrokerActive(AbstractConfiguration config) {
		if (config == null) {
			return false;
		}
		if (config.containsKey("wsbroker.active")) {
			return config.getBoolean("wsbroker.active", false);
		}
		if (config.containsKey("wsbroker[@active]")) {
			return config.getBoolean("wsbroker[@active]", false);
		}
		String endpoint = getWsBrokerEndpoint(config);
		return endpoint != null && !endpoint.isBlank();
	}

	public static String getClientName(AbstractConfiguration config) {
		if (config == null) {
			return DEFAULT_CLIENT_NAME;
		}
		String name = config.getString("wsbroker.name", null);
		if (name != null && !name.isBlank()) {
			return name.trim();
		}
		return config.getString("name", DEFAULT_CLIENT_NAME);
	}

	public static String getWsBrokerEndpoint(AbstractConfiguration config) {
		if (config == null) {
			return null;
		}
		String[] keys = new String[] {
				"wsbroker.url",
				"wsbroker.endpoint",
				"wsbroker.target_url",
				"wsbroker.targeturl",
				"wsbroker.uri",
				"wsbroker"
		};
		for (String key : keys) {
			String val = config.getString(key, null);
			if (val != null && !val.isBlank()) {
				return val.trim();
			}
		}
		return null;
	}

	public static List<String> getChannels(AbstractConfiguration config) {
		if (config == null) {
			return Collections.emptyList();
		}
		List<?> list = config.getList("wsbroker.channels.channel");
		if (list == null || list.isEmpty()) {
			list = config.getList("wsbroker.channels");
		}
		if (list == null || list.isEmpty()) {
			list = config.getList("channels.channel");
		}
		if (list == null || list.isEmpty()) {
			list = config.getList("channels");
		}
		if (list == null || list.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> channels = new ArrayList<>();
		for (Object obj : list) {
			if (obj != null) {
				String s = obj.toString().trim();
				if (!s.isEmpty()) {
					channels.add(s);
				}
			}
		}
		return channels;
	}

	public static CertManagerConfig getClientTlsConfig(AbstractConfiguration config) {
		if (config == null) {
			return null;
		}
		String filePath = config.getString("config.file.path", null);
		if (filePath != null) {
			File f = new File(filePath);
			if (f.exists()) {
				try {
					return XmlConfigParser.parse(f);
				} catch (IllegalArgumentException e) {
					logger.debug("No <clienttls> block found in configuration file: {}", e.getMessage());
					return null;
				} catch (Exception e) {
					logger.warn("Failed to parse <clienttls> from {}: {}", filePath, e.getMessage());
					return null;
				}
			}
		}
		return null;
	}

	public static CertManagerConfig getClientTlsConfig(File xmlFile) {
		if (xmlFile == null || !xmlFile.exists()) {
			return null;
		}
		try {
			return XmlConfigParser.parse(xmlFile);
		} catch (Exception e) {
			logger.warn("Failed to parse <clienttls> from file {}: {}", xmlFile.getAbsolutePath(), e.getMessage());
			return null;
		}
	}

	public static CertManagerConfig getClientTlsConfig(String xmlContent) {
		if (xmlContent == null || xmlContent.isBlank()) {
			return null;
		}
		try {
			return XmlConfigParser.parse(xmlContent);
		} catch (Exception e) {
			logger.warn("Failed to parse <clienttls> from XML content: {}", e.getMessage());
			return null;
		}
	}
}

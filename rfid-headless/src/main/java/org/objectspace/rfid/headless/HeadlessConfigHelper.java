package org.objectspace.rfid.headless;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
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

	/**
	 * Extracts all configured reader instances from the <readers><reader>...</reader></readers> section.
	 * If no <readers> section exists, falls back to generating a single HeadlessReaderConfig from the <device> section.
	 */
	public static List<HeadlessReaderConfig> getReaderConfigs(AbstractConfiguration config) {
		List<HeadlessReaderConfig> readerConfigs = new ArrayList<>();
		if (config == null) {
			HeadlessReaderConfig defaultConfig = new HeadlessReaderConfig(DEFAULT_CLIENT_NAME);
			defaultConfig.setReadOnly(false);
			defaultConfig.setType("usb");
			defaultConfig.setSleepInterval(DEFAULT_SLEEP_INTERVAL_MS);
			defaultConfig.setNumBlocks(DEFAULT_NUM_BLOCKS);
			defaultConfig.setDebounceMillis(DEFAULT_DEBOUNCE_MILLIS);
			readerConfigs.add(defaultConfig);
			return readerConfigs;
		}

		if (config instanceof HierarchicalConfiguration<?> hierConfig) {
			List<?> readerNodes = hierConfig.configurationsAt("readers.reader");
			if (readerNodes != null && !readerNodes.isEmpty()) {
				int index = 1;
				for (Object nodeObj : readerNodes) {
					if (nodeObj instanceof HierarchicalConfiguration<?> node) {
						HeadlessReaderConfig rCfg = parseReaderNode(node, config, index++);
						readerConfigs.add(rCfg);
					}
				}
				if (!readerConfigs.isEmpty()) {
					return readerConfigs;
				}
			}
		}

		// Fallback: Check if indexed properties exist in standard AbstractConfiguration (e.g. BaseConfiguration in tests)
		if (config.containsKey("readers.reader(0).reader_id") || config.containsKey("readers.reader(0).id") ||
				config.containsKey("readers.reader.reader_id") || config.containsKey("readers.reader.id")) {
			int idx = 0;
			while (true) {
				String prefix = "readers.reader(" + idx + ").";
				if (!config.containsKey(prefix + "reader_id") && !config.containsKey(prefix + "id") &&
						!config.containsKey(prefix + "device.feig.id") && !config.containsKey(prefix + "feig.id") &&
						!config.containsKey(prefix + "device_id") && !config.containsKey(prefix + "readonly") &&
						!config.containsKey(prefix + "read_only")) {
					if (idx == 0) {
						// Try without index prefix
						String singlePrefix = "readers.reader.";
						if (config.containsKey(singlePrefix + "reader_id") || config.containsKey(singlePrefix + "id") ||
								config.containsKey(singlePrefix + "device.feig.id") || config.containsKey(singlePrefix + "feig.id") ||
								config.containsKey(singlePrefix + "readonly") || config.containsKey(singlePrefix + "read_only")) {
							prefix = singlePrefix;
						} else {
							break;
						}
					} else {
						break;
					}
				}

				HeadlessReaderConfig rCfg = new HeadlessReaderConfig();
				String readerId = config.getString(prefix + "reader_id",
						config.getString(prefix + "id",
						config.getString(prefix + "readerId",
						config.getString(prefix + "name", "reader-" + (idx + 1)))));
				rCfg.setReaderId(readerId != null && !readerId.isBlank() ? readerId.trim() : "reader-" + (idx + 1));

				boolean ro = config.getBoolean(prefix + "readonly",
						config.getBoolean(prefix + "read_only",
						config.getBoolean(prefix + "readOnly", false)));
				rCfg.setReadOnly(ro);

				String devId = config.getString(prefix + "feig.id",
						config.getString(prefix + "device.feig.id",
						config.getString(prefix + "device_id",
						config.getString(prefix + "deviceId",
						config.getString(prefix + "feig_id", null)))));
				if (devId != null && !devId.isBlank()) {
					rCfg.setDeviceId(devId.trim());
				}

				String type = config.getString(prefix + "feig.type",
						config.getString(prefix + "device.feig.type",
						config.getString(prefix + "type", "usb")));
				rCfg.setType(type != null && !type.isBlank() ? type.trim() : "usb");

				String readerType = config.getString(prefix + "device.select",
						config.getString(prefix + "select",
						config.getString("device.select", "feig")));
				rCfg.setReaderType(readerType != null && !readerType.isBlank() ? readerType.trim() : "feig");

				rCfg.setSleepInterval(config.getInt(prefix + "sleep",
						config.getInt(prefix + "inventory.sleep", getSleepInterval(config))));
				rCfg.setNumBlocks(config.getInt(prefix + "numblocks", getNumBlocks(config)));
				rCfg.setDebounceMillis(parseDebounceFromPrefix(config, prefix, getDebounceMillis(config)));

				rCfg.setConfigFile(config.getString(prefix + "feig.configfile",
						config.getString(prefix + "device.feig.configfile",
						config.getString("device.feig.configfile", null))));
				rCfg.setRestoreConfig(config.getString(prefix + "feig.restoreconfig",
						config.getString(prefix + "device.feig.restoreconfig",
						config.getString("device.feig.restoreconfig", null))));
				rCfg.setStoreConfigFile(config.getString(prefix + "feig.storeconfigfile",
						config.getString(prefix + "device.feig.storeconfigfile",
						config.getString("device.feig.storeconfigfile", null))));
				rCfg.setHostConfigFile(config.getString(prefix + "feig.hostconfigfile",
						config.getString(prefix + "device.feig.hostconfigfile",
						config.getString("device.feig.hostconfigfile", null))));

				List<Object> chList = config.getList(prefix + "channels.channel");
				if (chList != null && !chList.isEmpty()) {
					List<String> channels = new ArrayList<>();
					for (Object obj : chList) {
						if (obj != null && !obj.toString().isBlank()) {
							channels.add(obj.toString().trim());
						}
					}
					rCfg.setChannels(channels);
				}

				rCfg.setRootConfiguration(config);
				readerConfigs.add(rCfg);
				idx++;
				if (prefix.equals("readers.reader.")) {
					break;
				}
			}
			if (!readerConfigs.isEmpty()) {
				return readerConfigs;
			}
		}

		// Single-Reader Legacy Fallback: Build from root <device> section
		HeadlessReaderConfig fallback = new HeadlessReaderConfig();
		fallback.setReaderId(getClientName(config));
		fallback.setReadOnly(config.getBoolean("device.readonly", config.getBoolean("readonly", false)));
		fallback.setDeviceId(config.getString("device.feig.id", null));
		fallback.setType(config.getString("device.feig.type", "usb"));
		fallback.setReaderType(config.getString("device.select", "feig"));
		fallback.setSleepInterval(getSleepInterval(config));
		fallback.setNumBlocks(getNumBlocks(config));
		fallback.setDebounceMillis(getDebounceMillis(config));
		fallback.setConfigFile(config.getString("device.feig.configfile", null));
		fallback.setRestoreConfig(config.getString("device.feig.restoreconfig", null));
		fallback.setStoreConfigFile(config.getString("device.feig.storeconfigfile", null));
		fallback.setHostConfigFile(config.getString("device.feig.hostconfigfile", null));
		fallback.setChannels(getChannels(config));
		fallback.setRootConfiguration(config);

		readerConfigs.add(fallback);
		return readerConfigs;
	}

	private static HeadlessReaderConfig parseReaderNode(HierarchicalConfiguration<?> node, AbstractConfiguration rootConfig, int defaultIndex) {
		HeadlessReaderConfig rCfg = new HeadlessReaderConfig();
		String readerId = node.getString("reader_id",
				node.getString("id",
				node.getString("readerId",
				node.getString("name", "reader-" + defaultIndex))));
		rCfg.setReaderId(readerId != null && !readerId.isBlank() ? readerId.trim() : "reader-" + defaultIndex);

		boolean readOnly = node.getBoolean("readonly",
				node.getBoolean("read_only",
				node.getBoolean("readOnly", false)));
		rCfg.setReadOnly(readOnly);

		String deviceId = node.getString("feig.id",
				node.getString("device.feig.id",
				node.getString("device_id",
				node.getString("deviceId",
				node.getString("feig_id", null)))));
		if (deviceId != null && !deviceId.isBlank()) {
			rCfg.setDeviceId(deviceId.trim());
		}

		String type = node.getString("feig.type",
				node.getString("device.feig.type",
				node.getString("type", "usb")));
		rCfg.setType(type != null && !type.isBlank() ? type.trim() : "usb");

		String readerType = node.getString("device.select",
				node.getString("select",
				rootConfig.getString("device.select", "feig")));
		rCfg.setReaderType(readerType != null && !readerType.isBlank() ? readerType.trim() : "feig");

		int sleepInterval = node.getInt("sleep",
				node.getInt("inventory.sleep", getSleepInterval(rootConfig)));
		rCfg.setSleepInterval(sleepInterval);

		int numBlocks = node.getInt("numblocks", getNumBlocks(rootConfig));
		rCfg.setNumBlocks(numBlocks);

		rCfg.setDebounceMillis(parseDebounceFromNode(node, getDebounceMillis(rootConfig)));

		rCfg.setConfigFile(node.getString("feig.configfile",
				node.getString("device.feig.configfile",
				rootConfig.getString("device.feig.configfile", null))));
		rCfg.setRestoreConfig(node.getString("feig.restoreconfig",
				node.getString("device.feig.restoreconfig",
				rootConfig.getString("device.feig.restoreconfig", null))));
		rCfg.setStoreConfigFile(node.getString("feig.storeconfigfile",
				node.getString("device.feig.storeconfigfile",
				rootConfig.getString("device.feig.storeconfigfile", null))));
		rCfg.setHostConfigFile(node.getString("feig.hostconfigfile",
				node.getString("device.feig.hostconfigfile",
				rootConfig.getString("device.feig.hostconfigfile", null))));

		List<Object> chList = node.getList("channels.channel");
		if (chList != null && !chList.isEmpty()) {
			List<String> channels = new ArrayList<>();
			for (Object obj : chList) {
				if (obj != null && !obj.toString().isBlank()) {
					channels.add(obj.toString().trim());
				}
			}
			rCfg.setChannels(channels);
		} else {
			rCfg.setChannels(getChannels(rootConfig));
		}

		rCfg.setRootConfiguration(rootConfig);
		return rCfg;
	}

	private static long parseDebounceFromNode(HierarchicalConfiguration<?> node, long defaultVal) {
		String[] keys = new String[] {
				"debounce",
				"inventory.debounce",
				"resend_interval",
				"resend_time",
				"tag_resend_time",
				"cooldown"
		};
		for (String key : keys) {
			if (node.containsKey(key)) {
				long val = node.getLong(key, -1L);
				if (val >= 0) {
					return val;
				}
			}
		}
		return defaultVal;
	}

	private static long parseDebounceFromPrefix(AbstractConfiguration config, String prefix, long defaultVal) {
		String[] keys = new String[] {
				"debounce",
				"inventory.debounce",
				"resend_interval",
				"resend_time",
				"tag_resend_time",
				"cooldown"
		};
		for (String key : keys) {
			if (config.containsKey(prefix + key)) {
				long val = config.getLong(prefix + key, -1L);
				if (val >= 0) {
					return val;
				}
			}
		}
		return defaultVal;
	}
}

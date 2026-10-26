package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Configuration model for a single RFID reader instance in rfid-headless.
 * Supports individual identification, read-only mode, hardware device parameters,
 * per-reader channels, and timing/memory overrides.
 */
public class HeadlessReaderConfig {

	private String readerId;
	private boolean readOnly;
	private String deviceId;
	private String type = "usb";
	private String readerType = "feig";
	private int sleepInterval = HeadlessConfigHelper.DEFAULT_SLEEP_INTERVAL_MS;
	private int numBlocks = HeadlessConfigHelper.DEFAULT_NUM_BLOCKS;
	private long debounceMillis = HeadlessConfigHelper.DEFAULT_DEBOUNCE_MILLIS;
	private List<String> channels = new ArrayList<>();
	private String configFile;
	private String restoreConfig;
	private String storeConfigFile;
	private String hostConfigFile;
	private AbstractConfiguration rootConfiguration;

	public HeadlessReaderConfig() {
	}

	public HeadlessReaderConfig(String readerId) {
		this.readerId = readerId;
	}

	public HeadlessReaderConfig(String readerId, boolean readOnly, String deviceId) {
		this.readerId = readerId;
		this.readOnly = readOnly;
		this.deviceId = deviceId;
	}

	public String getReaderId() {
		return readerId;
	}

	public void setReaderId(String readerId) {
		this.readerId = readerId;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReaderType() {
		return readerType;
	}

	public void setReaderType(String readerType) {
		this.readerType = readerType;
	}

	public int getSleepInterval() {
		return sleepInterval;
	}

	public void setSleepInterval(int sleepInterval) {
		this.sleepInterval = sleepInterval;
	}

	public int getNumBlocks() {
		return numBlocks;
	}

	public void setNumBlocks(int numBlocks) {
		this.numBlocks = numBlocks;
	}

	public long getDebounceMillis() {
		return debounceMillis;
	}

	public void setDebounceMillis(long debounceMillis) {
		this.debounceMillis = debounceMillis;
	}

	public List<String> getChannels() {
		return channels != null ? Collections.unmodifiableList(channels) : Collections.emptyList();
	}

	public void setChannels(List<String> channels) {
		this.channels = channels != null ? new ArrayList<>(channels) : new ArrayList<>();
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}

	public String getRestoreConfig() {
		return restoreConfig;
	}

	public void setRestoreConfig(String restoreConfig) {
		this.restoreConfig = restoreConfig;
	}

	public String getStoreConfigFile() {
		return storeConfigFile;
	}

	public void setStoreConfigFile(String storeConfigFile) {
		this.storeConfigFile = storeConfigFile;
	}

	public String getHostConfigFile() {
		return hostConfigFile;
	}

	public void setHostConfigFile(String hostConfigFile) {
		this.hostConfigFile = hostConfigFile;
	}

	public AbstractConfiguration getRootConfiguration() {
		return rootConfiguration;
	}

	public void setRootConfiguration(AbstractConfiguration rootConfiguration) {
		this.rootConfiguration = rootConfiguration;
	}

	/**
	 * Generates an effective AbstractConfiguration tailored for this reader instance.
	 * Preserves the full hierarchical structure of rootConfiguration (such as tagfeatures)
	 * and applies reader-specific overrides.
	 */
	public AbstractConfiguration toConfiguration() {
		AbstractConfiguration target = null;

		if (rootConfiguration != null) {
			try {
				Configuration cloned = ConfigurationUtils.cloneConfiguration(rootConfiguration);
				if (cloned instanceof AbstractConfiguration abstractCloned) {
					target = abstractCloned;
				}
			} catch (Exception ignored) {
			}

			if (target == null) {
				BaseConfiguration base = new BaseConfiguration();
				Iterator<String> keys = rootConfiguration.getKeys();
				while (keys.hasNext()) {
					String key = keys.next();
					base.setProperty(key, rootConfiguration.getProperty(key));
				}
				target = base;
			}
		} else {
			target = new BaseConfiguration();
		}

		// Apply reader-specific properties
		target.setProperty("device.select", readerType != null ? readerType : "feig");
		if (deviceId != null && !deviceId.isBlank()) {
			target.setProperty("device.feig.id", deviceId.trim());
		}
		if (type != null && !type.isBlank()) {
			target.setProperty("device.feig.type", type.trim());
		}
		if (configFile != null && !configFile.isBlank()) {
			target.setProperty("device.feig.configfile", configFile.trim());
		}
		if (restoreConfig != null && !restoreConfig.isBlank()) {
			target.setProperty("device.feig.restoreconfig", restoreConfig.trim());
		}
		if (storeConfigFile != null && !storeConfigFile.isBlank()) {
			target.setProperty("device.feig.storeconfigfile", storeConfigFile.trim());
		}
		if (hostConfigFile != null && !hostConfigFile.isBlank()) {
			target.setProperty("device.feig.hostconfigfile", hostConfigFile.trim());
		}

		target.setProperty("numblocks", numBlocks);
		target.setProperty("sleep", sleepInterval);
		target.setProperty("inventory.sleep", sleepInterval);
		target.setProperty("debounce", debounceMillis);

		return target;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HeadlessReaderConfig that = (HeadlessReaderConfig) o;
		return readOnly == that.readOnly &&
				sleepInterval == that.sleepInterval &&
				numBlocks == that.numBlocks &&
				debounceMillis == that.debounceMillis &&
				Objects.equals(readerId, that.readerId) &&
				Objects.equals(deviceId, that.deviceId) &&
				Objects.equals(type, that.type) &&
				Objects.equals(channels, that.channels);
	}

	@Override
	public int hashCode() {
		return Objects.hash(readerId, readOnly, deviceId, type, sleepInterval, numBlocks, debounceMillis, channels);
	}

	@Override
	public String toString() {
		return "HeadlessReaderConfig{" +
				"readerId='" + readerId + '\'' +
				", readOnly=" + readOnly +
				", deviceId='" + deviceId + '\'' +
				", type='" + type + '\'' +
				", sleepInterval=" + sleepInterval +
				", numBlocks=" + numBlocks +
				", debounceMillis=" + debounceMillis +
				", channels=" + channels +
				'}';
	}
}

/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * Based on HAWK RFID Library Tools:
 * Copyright 2015 Center for Information, Media and Technology (ZIMT),
 * HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of RFID Library Tools.
 * 
 * RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Diese Datei ist Teil von RFID Library Tools.
 *  
 * RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.feig;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration2.AbstractConfiguration;
import de.feig.fedm.Connector;
import de.feig.fedm.CustomXmlData;
import de.feig.fedm.ErrorCode;
import de.feig.fedm.InventoryParam;
import de.feig.fedm.ReaderInfo;
import de.feig.fedm.ReaderModule;
import de.feig.fedm.ReaderStatus;
import de.feig.fedm.ReaderType;
import de.feig.fedm.RequestMode;
import de.feig.fedm.TagItem;
import de.feig.fedm.UsbManager;
import de.feig.fedm.UsbScanInfo;
import de.feig.fedm.taghandler.ThBase;

/**
 * Class representing a FEIG reader using FEIG SDK Gen3 (FEDM Java API).
 * Actually supported: FEIG USB readers (such as ISC.MR102-USB).
 * 
 * @author Juergen Enge
 *
 */
public class FeigRFID {

	private final List<String> startupLogs = new java.util.concurrent.CopyOnWriteArrayList<>();
	private volatile boolean isMR102 = false;
	private volatile boolean hasCommunicationError = false;
	private volatile String lastErrorMessage = null;

	private void log(String msg) {
		System.out.println(msg);
		if (msg != null) {
			startupLogs.add(msg);
		}
	}

	private void setError(String msg) {
		hasCommunicationError = true;
		lastErrorMessage = msg;
		log("Reader-Fehler: " + msg);
	}

	private void clearError() {
		hasCommunicationError = false;
		lastErrorMessage = null;
	}

	public List<String> getStartupLogs() {
		return new ArrayList<>(startupLogs);
	}

	public String getStartupLog() {
		return String.join("\n", startupLogs);
	}

	public boolean isMR102() {
		return isMR102;
	}

	public boolean isMR102Detected() {
		return isMR102;
	}

	public void setMR102(boolean mr102) {
		this.isMR102 = mr102;
	}

	public boolean hasError() {
		return hasCommunicationError;
	}

	public boolean hasCommunicationError() {
		return hasCommunicationError;
	}

	public String getLastErrorMessage() {
		return lastErrorMessage;
	}

	public void setCommunicationError(boolean error, String message) {
		if (error) {
			setError(message != null ? message : "Unbekannter Fehler");
		} else {
			clearError();
		}
	}

	/**
	 * Constructor with abstract configuration
	 * 
	 * @param config
	 *            abstract configuration
	 */
	public FeigRFID(AbstractConfiguration config) {
		this.config = config;
	}

	/**
	 * Connects to the FEIG RFID reader.
	 * Reads optional configuration values:
	 * - device.feig.id (to bind to a specific hardware device ID in hex)
	 * - device.feig.type (must be "usb")
	 * 
	 * @throws Exception if connection fails
	 */
	public void connect() throws Exception {
		connected = false;
		reader = new ReaderModule(RequestMode.UniDirectional);
		String configDeviceID = null;
		String configDeviceType = null;
		if (config != null) {
			configDeviceID = config.getString("device.feig.id");
			String devType = config.getString("device.feig.type");
			configDeviceType = (devType != null) ? devType.toLowerCase() : null;
		}
		if (configDeviceType != null && !configDeviceType.equals("usb")) {
			String err = "Device Type " + configDeviceType + " not supported.";
			setError(err);
			throw new Exception(err);
		}

		int scanRes = UsbManager.startDiscover();
		if (scanRes < 0) {
			String err = "usb scan failed: " + ErrorCode.toString(scanRes);
			setError(err);
			throw new Exception(err);
		}

		long foundDeviceID = 0;
		UsbScanInfo scanInfo;
		while ((scanInfo = UsbManager.popDiscover()) != null && scanInfo.isValid()) {
			String devIdHex = scanInfo.deviceIdToHexString();
			long devId = scanInfo.deviceId();
			long readerType = scanInfo.readerType();
			String rTypeStr = scanInfo.readerTypeToString();
			log("Device found: " + devIdHex + (rTypeStr != null ? " (" + rTypeStr + ")" : ""));
			if (readerType == ReaderType.MR102 || (rTypeStr != null && rTypeStr.toUpperCase().contains("MR102"))) {
				isMR102 = true;
			}
			if (configDeviceID == null || configDeviceID.equalsIgnoreCase(devIdHex)) {
				foundDeviceID = devId;
			}
		}
		UsbManager.stopDiscover();

		if (foundDeviceID == 0) {
			String err = "no device found";
			setError(err);
			throw new Exception(err);
		}

		currentDeviceID = foundDeviceID;
		String hexId = String.format("%08X", currentDeviceID);
		log("Connecting to: " + hexId);

		Connector connector = Connector.createUsbConnector(currentDeviceID);
		int connRes = reader.connect(connector);
		if (connRes != ErrorCode.Ok) {
			String err = "connect failed: " + ErrorCode.toString(connRes);
			setError(err);
			throw new Exception(err);
		}

		int infoRes = reader.readReaderInfo();
		ReaderInfo info = reader.info();
		if (infoRes == ErrorCode.Ok && info != null) {
			log(info.getReport());
			long rType = info.readerType();
			String rTypeStr = info.readerTypeToString();
			if (rType == ReaderType.MR102 || (rTypeStr != null && rTypeStr.toUpperCase().contains("MR102"))) {
				isMR102 = true;
			}
		}
		deviceInfo = "FEIG " + (info != null && info.readerTypeToString() != null ? info.readerTypeToString() : "ISC.MR102-USB") + " (" + hexId + ")";
		connected = true;
		clearError();
	}

	/**
	 * Returns connection state
	 * @return true if connected
	 */
	public boolean isConnected() {
		return connected && reader != null && reader.isConnected();
	}

	/**
	 * Verifies if the reader is still reachable via USB.
	 * If not reachable or unplugged, sets connection state to false and disconnects cleanly.
	 * @return true if connected and reachable
	 */
	public synchronized boolean checkConnection() {
		if (!connected || reader == null) {
			return false;
		}
		try {
			int scanRes = UsbManager.startDiscover();
			if (scanRes < 0) {
				setError("Reader connection lost (USB scan error: " + ErrorCode.toString(scanRes) + ")");
				disconnectInternal();
				return false;
			}
			boolean found = false;
			UsbScanInfo scanInfo;
			while ((scanInfo = UsbManager.popDiscover()) != null && scanInfo.isValid()) {
				long rType = scanInfo.readerType();
				String rTypeStr = scanInfo.readerTypeToString();
				if (rType == ReaderType.MR102 || (rTypeStr != null && rTypeStr.toUpperCase().contains("MR102"))) {
					isMR102 = true;
				}
				if (scanInfo.deviceId() == currentDeviceID || currentDeviceID == 0) {
					found = true;
					break;
				}
			}
			UsbManager.stopDiscover();
			if (!found) {
				setError("RFID-Leser nicht mehr erreichbar (nicht verbunden)");
				disconnectInternal();
				return false;
			}
			boolean isConn = reader.isConnected();
			if (!isConn) {
				setError("RFID-Leser Verbindung unterbrochen");
			}
			return isConn;
		} catch (Throwable t) {
			setError("RFID-Leser Verbindungspruefung fehlgeschlagen: " + t.getMessage());
			disconnectInternal();
			return false;
		}
	}

	/**
	 * Returns descriptive information about the connected device.
	 * @return device information or null
	 */
	public String getDeviceInfo() {
		return connected ? deviceInfo : null;
	}

	/**
	 * Internal safe disconnect without throwing exceptions.
	 */
	public synchronized void disconnectInternal() {
		connected = false;
		if (reader != null) {
			try {
				reader.disconnect();
			} catch (Throwable t) {
				// Ignore
			}
		}
	}

	/**
	 * Initializes the reader.
	 * Reads optional configuration values:
	 * - device.feig.storeconfigfile
	 * - device.feig.configfile (name of firmware configuration)
	 * 
	 * @throws Exception if initialization fails
	 */
	public void init() throws Exception {
		try {
			if (config != null) {
				String storeConfigFile = config.getString("device.feig.storeconfigfile", null);
				if (storeConfigFile != null) {
					if (Files.isRegularFile(Paths.get(storeConfigFile))) {
						Files.delete(Paths.get(storeConfigFile));
					}
					log("Storing actual configuration to " + storeConfigFile);
					copyConfigToFile(storeConfigFile);
				}
				String readerConfigFile = config.getString("device.feig.configfile", null);
				if (readerConfigFile != null) {
					if (!Files.isRegularFile(Paths.get(readerConfigFile))) {
						throw new Exception("configfile " + readerConfigFile + " not a regular file");
					}
					log("Loading configuration file: " + readerConfigFile);
					copyFileToConfig(readerConfigFile);
				}
			}
			clearError();
		} catch (Exception e) {
			setError("Initialisierung fehlgeschlagen: " + e.getMessage());
			throw e;
		}
	}

	/**
	 * Closes the connections and restores configuration (optional).
	 * @throws Exception if restore fails
	 */
	public void close() throws Exception {
		if (config != null) {
			String restoreconfig = config.getString("device.feig.restoreconfig", null);
			if (restoreconfig != null) {
				if (!Files.isRegularFile(Paths.get(restoreconfig))) {
					throw new Exception("restoreconfigfile " + restoreconfig + " not a regular file");
				}
				log("restoring configuration from " + restoreconfig);
				copyFileToConfig(restoreconfig);
			}
		}
		if (reader != null && connected) {
			try {
				reader.disconnect();
			} catch (Exception e) {
				// Ignore
			}
		}
		connected = false;
	}

	/**
	 * Formats error/status codes into human readable strings using ErrorCode or ReaderStatus.
	 * 
	 * @param code error or status code
	 * @return formatted status message
	 */
	public static String formatError(int code) {
		if (code == ErrorCode.Ok) {
			return "OK";
		}
		if (code == 130) {
			return "CmdNotAvailable (130)";
		}
		if (code < 0) {
			try {
				return ErrorCode.toString(code);
			} catch (Throwable t) {
				return "ErrorCode (" + code + ")";
			}
		}
		try {
			String statusStr = ReaderStatus.toString(code);
			if (statusStr != null && !statusStr.isEmpty() && !statusStr.startsWith("Reader status (")) {
				return statusStr + " (" + code + ")";
			}
		} catch (Throwable t) {
			// Fallback if native library is not loaded
		}
		return "Status (" + code + ")";
	}

	/**
	 * Stores firmware configuration in xml-file.
	 * 
	 * @param fileName xml-filename
	 * @throws Exception if read or transfer fails
	 */
	public void copyConfigToFile(String fileName) throws Exception {
		synchronized (readerLock) {
			if (reader == null) {
				throw new Exception("Reader not initialized");
			}
			int res = reader.config().readCompleteConfiguration(false);
			if (res != ErrorCode.Ok) {
				throw new Exception("readCompleteConfiguration failed: " + formatError(res));
			}
			res = reader.config().transferReaderCfgToXmlFile(fileName);
			if (res != ErrorCode.Ok) {
				throw new Exception("transferReaderCfgToXmlFile failed: " + formatError(res));
			}
		}
	}

	/**
	 * Writes firmware configuration to connected hardware device and waits for EEPROM write completion.
	 * 
	 * @param fileName xml-filename
	 * @throws Exception if transfer fails
	 */
	public void copyFileToConfig(String fileName) throws Exception {
		synchronized (readerLock) {
			if (reader == null) {
				throw new Exception("Reader not initialized");
			}
			int res = reader.config().transferXmlFileToReaderCfg(fileName);
			if (res != ErrorCode.Ok) {
				// Fallback: deserialize and write supported configuration pages individually
				CustomXmlData customXmlData = new CustomXmlData();
				int desRes = reader.config().deserializeFromFile(fileName, customXmlData);
				if (desRes == ErrorCode.Ok) {
					long[] targetPages = new long[] { 1, 2, 3, 4, 5, 11, 12, 13 };
					int successCount = 0;
					for (long page : targetPages) {
						try {
							int wEeprom = reader.config().writeConfigurationPage(page, false);
							int wRam = reader.config().writeConfigurationPage(page, true);
							if (wEeprom == ErrorCode.Ok || wRam == ErrorCode.Ok) {
								successCount++;
							}
						} catch (Throwable t) {
							// Ignore unsupported page write errors
						}
					}
					if (successCount > 0) {
						try {
							reader.config().applyConfiguration(true);
						} catch (Throwable t) {
							// Best effort apply
						}
						waitForEepromWrite(1500);
						return;
					}
				}
				throw new Exception("transferXmlFileToReaderCfg failed: " + formatError(res));
			}
			waitForEepromWrite(1500);
		}
	}

	/**
	 * Waits for the reader EEPROM write cycle to complete.
	 * 
	 * @param millis duration in milliseconds to wait
	 */
	public void waitForEepromWrite(long millis) {
		long waitTime = millis > 0 ? millis : 1500;
		log("Warte auf Abschluss des EEPROM-Schreibvorgangs (" + waitTime + " ms)...");
		try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Performs a system reset on the connected FEIG RFID reader (systemReset).
	 * 
	 * @throws Exception if reader is not initialized
	 */
	public void systemReset() throws Exception {
		synchronized (readerLock) {
			if (reader == null) {
				throw new Exception("Reader not initialized");
			}
			log("Führe System-Reset auf dem RFID-Lesegerät durch (systemReset)...");
			int res = reader.softReset();
			if (res != ErrorCode.Ok) {
				res = reader.hardReset();
			}
			if (res != ErrorCode.Ok) {
				log("Hinweis: systemReset meldete: " + formatError(res));
			} else {
				log("System-Reset erfolgreich an das Lesegerät übermittelt.");
			}
		}
	}

	/**
	 * Restores reader configuration from an XML file, waits for EEPROM write completion,
	 * and performs a system reset.
	 * 
	 * @param targetFile source XML configuration file
	 * @throws Exception on restore failure
	 */
	public void restoreConfiguration(File targetFile) throws Exception {
		if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
			throw new IllegalArgumentException("Konfigurationsdatei existiert nicht oder ist ungültig");
		}
		synchronized (readerLock) {
			if (!isConnected() || reader == null) {
				connect();
			}
			log("Schreibe Konfiguration in den RFID-Leser aus: " + targetFile.getAbsolutePath());
			copyFileToConfig(targetFile.getAbsolutePath());
			systemReset();
			log("Konfiguration erfolgreich auf dem RFID-Leser wiederhergestellt und Lesegerät neu gestartet.");
		}
	}

	/**
	 * Backup reader configuration to the specified target file.
	 * 
	 * @param targetFile destination XML file
	 * @throws Exception on backup failure
	 */
	public void backupConfiguration(File targetFile) throws Exception {
		if (targetFile == null) {
			throw new IllegalArgumentException("Target backup file must not be null");
		}
		if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
			targetFile.getParentFile().mkdirs();
		}
		synchronized (readerLock) {
			if (!isConnected() || reader == null) {
				connect();
			}
			int res = reader.config().readCompleteConfiguration(false);
			if (res != ErrorCode.Ok) {
				String err = "readCompleteConfiguration failed: " + formatError(res);
				setError(err);
				throw new Exception(err);
			}
			res = reader.config().transferReaderCfgToXmlFile(targetFile.getAbsolutePath());
			if (res != ErrorCode.Ok) {
				String err = "transferReaderCfgToXmlFile failed: " + formatError(res);
				setError(err);
				throw new Exception(err);
			}
 		log("Reader-Konfiguration erfolgreich gesichert in: " + targetFile.getAbsolutePath());
		}
	}

	/**
	 * Resolves the reader_host_mode_config.xml configuration file from custom path,
	 * local directory, or classpath resources.
	 * 
	 * @param config application configuration
	 * @return resolved File
	 */
	public static File resolveHostModeConfigFile(AbstractConfiguration config) {
		String customPath = (config != null) ? config.getString("device.feig.hostconfigfile", null) : null;
		if (customPath != null && !customPath.trim().isEmpty()) {
			File f = new File(customPath.trim());
			if (f.exists() && f.isFile()) {
				return f;
			}
		}
		File localFile = new File("reader_host_mode_config.xml");
		if (localFile.exists() && localFile.isFile()) {
			return localFile;
		}
		File subFile = new File("rfid-inventory", "reader_host_mode_config.xml");
		if (subFile.exists() && subFile.isFile()) {
			return subFile;
		}
		try (InputStream in = FeigRFID.class.getResourceAsStream("/reader_host_mode_config.xml")) {
			if (in != null) {
				File tempResourceFile = File.createTempFile("feig_host_mode_config_", ".xml");
				tempResourceFile.deleteOnExit();
				Files.copy(in, tempResourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				return tempResourceFile;
			}
		} catch (Throwable t) {
			// Resource extraction fallback error ignored
		}
		return localFile;
	}

	/**
	 * Configures Host-Mode on the FEIG RFID reader by applying the XML configuration file,
	 * waiting for EEPROM completion, and issuing a hardware system reset.
	 * 
	 * @param configFile XML configuration file (reader_host_mode_config.xml)
	 * @throws Exception on failure
	 */
	public void configureHostMode(File configFile) throws Exception {
		restoreConfiguration(configFile);
	}

	/**
	 * Get last reader error.
	 * 
	 * @return last error of reader
	 */
	public int getLastError() {
		return reader != null ? reader.lastError() : 0;
	}

	/**
	 * Executes an inventory of tags.
	 * 
	 * @param all automatic mode (should be true)
	 * @param mode manual control
	 * @param antennas flag field with antennas
	 * @return list of transponder tag handlers
	 * @throws Exception if inventory fails
	 */
	public List<ThBase> tagInventory(boolean all, byte mode, byte antennas) throws Exception {
		synchronized (readerLock) {
			if (!connected || reader == null) {
				return new ArrayList<>();
			}
			try {
				InventoryParam param = new InventoryParam();
				if (antennas != 0) {
					param.setAntennas(antennas);
				}
				int back = reader.hm().inventory(all, param);
				if (back == 1 || back == ErrorCode.NoData) {
					return new ArrayList<>();
				}
				if (back != ErrorCode.Ok) {
					String err = "tag inventory error: " + formatError(back);
					setError(err);
					if (reader == null || !reader.isConnected()) {
						disconnectInternal();
					}
					throw new Exception(err);
				}
				clearError();
				List<ThBase> list = new ArrayList<>();
				long itemCount = reader.hm().itemCount();
				for (long i = 0; i < itemCount; i++) {
					TagItem item = reader.hm().tagItem(i);
					ThBase th = null;
					if (item != null && item.isValid()) {
						th = reader.hm().createTagHandler(item);
					}
					if (th == null) {
						th = reader.hm().createTagHandler(i);
					}
					if (th != null) {
						list.add(th);
					}
				}
				if (list.isEmpty()) {
					TagItem item;
					while ((item = reader.hm().popItem()) != null && item.isValid()) {
						ThBase th = reader.hm().createTagHandler(item);
						if (th != null) {
							list.add(th);
						}
					}
				}
				return list;
			} catch (Throwable t) {
				if (reader == null || !reader.isConnected()) {
					disconnectInternal();
				}
				setError("Inventory scan error: " + t.getMessage());
				throw (t instanceof Exception) ? (Exception) t : new Exception("tag inventory error", t);
			}
		}
	}

	/**
	 * Executes an inventory of tags with default parameters.
	 * 
	 * @param all automatic mode
	 * @return list of transponder tag handlers
	 * @throws Exception if inventory fails
	 */
	public List<ThBase> tagInventory(boolean all) throws Exception {
		return tagInventory(all, (byte) 0, (byte) 0);
	}

	public ReaderModule getReader() {
		return reader;
	}

	public long getCurrentDeviceID() {
		return currentDeviceID;
	}

	private final Object readerLock = new Object();
	protected AbstractConfiguration config;
	protected ReaderModule reader = null;
	protected long currentDeviceID = 0;
	protected String deviceInfo = null;
	private volatile boolean connected = false;

}

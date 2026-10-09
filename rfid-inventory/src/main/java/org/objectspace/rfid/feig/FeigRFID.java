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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration2.AbstractConfiguration;
import de.feig.fedm.Connector;
import de.feig.fedm.ErrorCode;
import de.feig.fedm.InventoryParam;
import de.feig.fedm.ReaderInfo;
import de.feig.fedm.ReaderModule;
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

	private void log(String msg) {
		System.out.println(msg);
		if (msg != null) {
			startupLogs.add(msg);
		}
	}

	public List<String> getStartupLogs() {
		return new ArrayList<>(startupLogs);
	}

	public String getStartupLog() {
		return String.join("\n", startupLogs);
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
			throw new Exception("Device Type " + configDeviceType + " not supported.");
		}

		int scanRes = UsbManager.startDiscover();
		if (scanRes < 0) {
			throw new Exception("usb scan failed: " + ErrorCode.toString(scanRes));
		}

		long foundDeviceID = 0;
		UsbScanInfo scanInfo;
		while ((scanInfo = UsbManager.popDiscover()) != null && scanInfo.isValid()) {
			String devIdHex = scanInfo.deviceIdToHexString();
			long devId = scanInfo.deviceId();
			log("Device found: " + devIdHex);
			if (configDeviceID == null || configDeviceID.equalsIgnoreCase(devIdHex)) {
				foundDeviceID = devId;
			}
		}
		UsbManager.stopDiscover();

		if (foundDeviceID == 0) {
			throw new Exception("no device found");
		}

		currentDeviceID = foundDeviceID;
		String hexId = String.format("%08X", currentDeviceID);
		log("Connecting to: " + hexId);

		Connector connector = Connector.createUsbConnector(currentDeviceID);
		int connRes = reader.connect(connector);
		if (connRes != ErrorCode.Ok) {
			throw new Exception("connect failed: " + ErrorCode.toString(connRes));
		}

		int infoRes = reader.readReaderInfo();
		ReaderInfo info = reader.info();
		if (infoRes == ErrorCode.Ok && info != null) {
			log(info.getReport());
		}
		deviceInfo = "FEIG " + (info != null && info.readerTypeToString() != null ? info.readerTypeToString() : "ISC.MR102-USB") + " (" + hexId + ")";
		connected = true;
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
				disconnectInternal();
				return false;
			}
			boolean found = false;
			UsbScanInfo scanInfo;
			while ((scanInfo = UsbManager.popDiscover()) != null && scanInfo.isValid()) {
				if (scanInfo.deviceId() == currentDeviceID || currentDeviceID == 0) {
					found = true;
					break;
				}
			}
			UsbManager.stopDiscover();
			if (!found) {
				disconnectInternal();
				return false;
			}
			return reader.isConnected();
		} catch (Throwable t) {
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
	 * Stores firmware configuration in xml-file.
	 * 
	 * @param fileName xml-filename
	 * @throws Exception if read or transfer fails
	 */
	public void copyConfigToFile(String fileName) throws Exception {
		if (reader == null) {
			throw new Exception("Reader not initialized");
		}
		int res = reader.config().readCompleteConfiguration(false);
		if (res != ErrorCode.Ok) {
			throw new Exception("readCompleteConfiguration failed: " + ErrorCode.toString(res));
		}
		res = reader.config().transferReaderCfgToXmlFile(fileName);
		if (res != ErrorCode.Ok) {
			throw new Exception("transferReaderCfgToXmlFile failed: " + ErrorCode.toString(res));
		}
	}

	/**
	 * Writes firmware configuration to connected hardware device.
	 * 
	 * @param fileName xml-filename
	 * @throws Exception if transfer fails
	 */
	public void copyFileToConfig(String fileName) throws Exception {
		if (reader == null) {
			throw new Exception("Reader not initialized");
		}
		int res = reader.config().transferXmlFileToReaderCfg(fileName);
		if (res != ErrorCode.Ok) {
			throw new Exception("transferXmlFileToReaderCfg failed: " + ErrorCode.toString(res));
		}
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
				if (reader == null || !reader.isConnected()) {
					disconnectInternal();
				}
				throw new Exception("tag inventory error: " + ErrorCode.toString(back));
			}
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
			throw (t instanceof Exception) ? (Exception) t : new Exception("tag inventory error", t);
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

	protected AbstractConfiguration config;
	protected ReaderModule reader = null;
	protected long currentDeviceID = 0;
	protected String deviceInfo = null;
	private volatile boolean connected = false;

}

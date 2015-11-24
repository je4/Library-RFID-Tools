/*******************************************************************************
 * Copyright 2015
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of HAWK RFID Library Tools.
 * 
 * HAWK RFID Library Tools is free software: you can redistribute it and/or modify
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
 * Diese Datei ist Teil von HAWK RFID Library Tools.
 *  
 * HAWK RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
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
import java.util.HashMap;
import org.apache.commons.configuration2.AbstractConfiguration;
import de.feig.FeHexConvert;
import de.feig.FePortDriverException;
import de.feig.FeReaderDriverException;
import de.feig.FeUsb;
import de.feig.FeUsbScanSearch;
import de.feig.FedmBrmTableItem;
import de.feig.FedmException;
import de.feig.FedmIscReader;
import de.feig.FedmIscReaderConst;
import de.feig.FedmIscReaderInfo;
import de.feig.TagHandler.FedmIscTagHandler;

/**
 * class representing a feig reader actually supported: ISC.MR102-USB
 * 
 * @author Juergen Enge
 *
 */
public class FeigRFID {

	/**
	 * constructor with abstract configuration
	 * 
	 * @param config
	 *            abstract configuration
	 */
	public FeigRFID(AbstractConfiguration config) {
		this.config = config;
		// build RFID feature map
	}

	/**
	 * connects to the feig rfid reader needs optional configuration values: *
	 * device.feig.id (to bind to a specific hardware) * device.feig.type (must
	 * be "usb")
	 * 
	 * @throws Exception
	 * @throws FedmException
	 * 
	 */
	public void connect() throws FedmException, Exception {
		usbHelper = new FeUsb();
		reader = new FedmIscReader();
		String configDeviceID = null;
		String configDeviceType = null;
		if (config != null) {
			configDeviceID = config.getString("device.feig.id");
			configDeviceType = config.getString("device.feig.type").toLowerCase();
		}
		// actually i have only USB to test...
		if (configDeviceType != null && !configDeviceType.equals("usb"))
			throw new Exception("Device Type " + configDeviceType + " not supported.");

		FeUsbScanSearch scanSearch = null;
		int back = usbHelper.scan(FeUsbScanSearch.SCAN_ALL, scanSearch);
		if (back != 0) {
			throw new Exception("usb scan failed");
		}
		int scanListSize = usbHelper.getScanListSize();
		for (int i = 0; i < scanListSize; i++) {
			String scanListPara = usbHelper.getScanListPara(i, "Device-ID");
			long deviceID = FeHexConvert.hexStringToLong(scanListPara);
			System.out.println("Device found: " + scanListPara);
			if (configDeviceID == null || configDeviceID.equals(scanListPara)) {
				currentDeviceID = deviceID;
			}
		}
		if (currentDeviceID == 0)
			throw new Exception("no device found");
		System.out.println("Connecting to: " + FeHexConvert.longToHexString(currentDeviceID));
		reader.connectUSB(currentDeviceID);
		FedmIscReaderInfo info = reader.getReaderInfo();
		System.out.println(info.getReport());
	}

	/**
	 * initializes the reader to deal with BRM or ISO tags needs optional
	 * configuration value * device.feig.storeconfigfile *
	 * device.feig.configfile (name of firmware configuration)
	 * 
	 * @throws Exception
	 */
	public void init() throws Exception {
		String storeConfigFile = config.getString("device.feig.storeconfigfile", null);
		if (storeConfigFile != null) {
			if (Files.isRegularFile(Paths.get(storeConfigFile))) {
				Files.delete(Paths.get(storeConfigFile));
			}
			System.out.println("Storing actual configuration to " + storeConfigFile);
			copyConfigToFile( storeConfigFile );
		}
		String readerConfigFile = config.getString("device.feig.configfile", null);
		if (readerConfigFile != null) {
			if (!Files.isRegularFile(Paths.get(readerConfigFile))) {
				throw new Exception("configfile " + readerConfigFile + " not a regular file");
			}
			System.out.println("Loading configuration file: " + readerConfigFile);
			copyFileToConfig(readerConfigFile);
		}

		reader.setTableSize(FedmIscReaderConst.ISO_TABLE, 17496);
		reader.setTableSize(FedmIscReaderConst.BRM_TABLE, 1104);
	}

	/**
	 * closes the connections and restores configuration (optional)
	 * @throws Exception 
	 */
	public void close() throws Exception {
		String restoreconfig = config.getString("device.feig.restoreconfig", null);
		if (restoreconfig != null) {
			if (!Files.isRegularFile(Paths.get(restoreconfig))) {
				throw new Exception("restoreconfigfile " + restoreconfig + " not a regular file");
			}
			System.out.println("restoring configuration from " + restoreconfig);
			copyFileToConfig(restoreconfig);
		}
		reader.disConnect();
	}

	/**
	 * stores firmware configuration in xml-file
	 * 
	 * @param fileName xml-filename
	 * @throws FedmException
	 * @throws FeReaderDriverException
	 * @throws FePortDriverException
	 */
	public void copyConfigToFile(String fileName) throws FedmException, FePortDriverException, FeReaderDriverException {
		reader.readCompleteConfiguration(false);
		reader.transferReaderCfgToXmlFile(fileName);
	}

	/**
	 * writes firmware configuration to connected hardware device
	 * 
	 * @param fileName
	 *            xml-filename
	 * @throws FedmException
	 */
	public void copyFileToConfig(String fileName) throws FedmException {
		reader.transferXmlFileToReaderCfg(fileName);
	}

	/**
	 * get last reader error
	 * 
	 * @return last error of reader
	 */
	public int getLastError() {
		return reader.getLastError();
	}

	/**
	 * executes an inventory of tags
	 * 
	 * @param all
	 *            automatic mode (should be true)
	 * @param mode
	 *            manual control (should be 0)
	 * @param antennas
	 *            flag field with antennas (should be 1)
	 * @return map of transponders
	 * @throws FedmException
	 * @throws FePortDriverException
	 * @throws FeReaderDriverException
	 */
	HashMap<String, FedmIscTagHandler> tagInventory(boolean all, byte mode, byte antennas)
			throws FedmException, FePortDriverException, FeReaderDriverException {
		return reader.tagInventory(all, mode, antennas);
	}

	protected AbstractConfiguration config;
	protected FeUsb usbHelper = null;
	protected FedmIscReader reader = null;
	protected long currentDeviceID = 0;

}

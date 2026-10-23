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
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;

import de.feig.fedm.taghandler.ThBase;
import de.feig.fedm.taghandler.ThIso15693;
import de.feig.fedm.types.DataBuffer;
import de.feig.fedm.types.LongRef;

/**
 * ISO15693 compliant tag reader for feig hardware devices
 * @author Juergen Enge
 *
 */
public class ISO15693Feig implements ISO15693Reader {

	/**
	 * constructor
	 * reads configuration data
	 * * tagfeatures.tag<i>.name (name of ISO15693 tag)
	 * * tagfeatures.tag<i>.usermemory (number of bits, which are stored on tag)
	 * * tagfeatures.tag<i>.blocksize (number of bytes per block)
	 * @param config abstract configuration
	 */
	public ISO15693Feig(AbstractConfiguration config) {
		assert config != null;
		
		this.config = config;
		feig = new FeigRFID( config );
		maxBlocksMap = new HashMap<String, Integer>();
		if (config != null) {
			List<Object> tagFeatures = config.getList("tagfeatures.tag.name");
			for (int i = 0; i < tagFeatures.size(); i++) {
				String tagName = config.getString("tagfeatures.tag(" + i + ").name");
				int userMemory = config.getInt("tagfeatures.tag(" + i + ").usermemory");
				int blockSize = config.getInt("tagfeatures.tag(" + i + ").blocksize");
				int blocks = userMemory / (blockSize * 8);
				maxBlocksMap.put(tagName, blocks);
			}
		}
	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#connect() 
	 */
	public void connect() throws Exception {
		feig.connect();

	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#init() 
	 */
	public void init() throws Exception {
		feig.init();

	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#isConnected() 
	 */
	@Override
	public boolean isConnected() {
		return feig != null && feig.isConnected();
	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#checkConnection()
	 */
	@Override
	public boolean checkConnection() {
		return feig != null && feig.checkConnection();
	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#getDeviceInfo()
	 */
	@Override
	public String getDeviceInfo() {
		return feig != null ? feig.getDeviceInfo() : null;
	}

	@Override
	public String getDeviceId() {
		return feig != null ? feig.getDeviceId() : null;
	}

	@Override
	public String getDeviceType() {
		return feig != null ? feig.getDeviceType() : null;
	}

	@Override
	public String getInitDescription() {
		return feig != null ? feig.getInitDescription() : "";
	}

	@Override
	public List<String> getStartupLogs() {
		return feig != null ? feig.getStartupLogs() : java.util.Collections.emptyList();
	}

	@Override
	public String getStartupLog() {
		return feig != null ? feig.getStartupLog() : "";
	}

	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#close() 
	 */
	public void close() throws Exception {
		if (feig != null) {
			feig.close();
		}
	}

	
	/**
	 * @see org.objectspace.rfid.library.ISO15693Reader#inventory(org.objectspace.rfid.TagCallback, int)
	 */
	@Override
	public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
		if (!isConnected()) {
			return;
		}

		List<ThBase> tagHandlers = feig.tagInventory(true, (byte) 0, (byte) 0);
		int totalTags = tagHandlers != null ? tagHandlers.size() : 0;
		if (totalTags == 0) {
			inventoryCallback.empty();
			return;
		}

		int counter = 0;
		for (ThBase tagHandler : tagHandlers) {
			try {
				if (tagHandler != null) {
					// get size of tag from configuration or guess something
					String tagName = tagHandler.transponderName();
					Integer maxBlocks = maxBlocksMap != null ? maxBlocksMap.get(tagName) : null;
					// tagName not in configuration
					if (maxBlocks == null) {
						maxBlocks = numBlocks;
						if (maxBlocksMap != null) {
							maxBlocksMap.put(tagName, maxBlocks);
						}
					}

					// paranoia don't read too much
					int blocksToRead = Math.min(numBlocks, maxBlocks);

					// read data blocks
					LongRef blockSizeRef = new LongRef();
					DataBuffer dataBuffer = new DataBuffer();
					int back = tagHandler.readMultipleBlocks(0, blocksToRead, blockSizeRef, dataBuffer);
					byte[] data = dataBuffer.data();
					if ((data == null || data.length == 0) && blocksToRead > 4) {
						dataBuffer = new DataBuffer();
						back = tagHandler.readMultipleBlocks(0, 4, blockSizeRef, dataBuffer);
						data = dataBuffer.data();
						if (data != null && data.length > 0) {
							blocksToRead = 4;
						}
					}
					if (data == null || data.length == 0) {
						continue;
					}

					String mfrName = (tagHandler instanceof ThIso15693) ? ((ThIso15693) tagHandler).manufacturerName() : "";
					byte[] newBlock = inventoryCallback.doIt(
						counter,
						totalTags,
						mfrName,
						tagName,
						tagHandler.iddToHexString(),
						data,
						blocksToRead
					);
					if (newBlock != null) {
					}
				} else {
					System.out.println("Tag handler is null");
				}
			} catch (Exception ex) {
				System.err.println("Tag processing error: " + ex.getMessage());
			} finally {
				try {
					if (tagHandler != null) {
						tagHandler.close();
					}
				} catch (Throwable t) {
					// Ignore
				}
			}
			counter++;
		}
	}
	@Override
	public boolean isMR102() {
		return feig != null && feig.isMR102();
	}

	@Override
	public boolean isHostMode() {
		return feig != null && feig.isHostMode();
	}

	@Override
	public boolean hasError() {
		return feig != null && feig.hasError();
	}

	@Override
	public String getLastErrorDetails() {
		return feig != null ? feig.getLastErrorMessage() : null;
	}

	@Override
	public void backupConfig(File targetFile) throws Exception {
		if (feig == null) {
			throw new Exception("FeigRFID instance not initialized");
		}
		feig.backupConfiguration(targetFile);
	}

	@Override
	public void restoreConfig(File sourceFile) throws Exception {
		if (feig == null) {
			throw new Exception("FeigRFID instance not initialized");
		}
		feig.restoreConfiguration(sourceFile);
	}

	@Override
	public void systemReset() throws Exception {
		if (feig != null) {
			feig.systemReset();
		}
	}

	@Override
	public void restoreHostModeConfig(File configFile) throws Exception {
		if (feig == null) {
			throw new Exception("FeigRFID instance not initialized");
		}
		feig.configureHostMode(configFile);
	}

	@Override
	public File resolveHostModeConfigFile() {
		return FeigRFID.resolveHostModeConfigFile(config);
	}

	@Override
	public void writeTag(String uid, byte[] data, int blockSize) throws Exception {
		if (!isConnected()) {
			throw new Exception("RFID reader is not connected");
		}
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("No data provided to write to tag");
		}

		String targetUid = uid != null ? uid.replaceAll("[^a-fA-F0-9]", "").toUpperCase() : "";

		List<ThBase> tagHandlers = feig.tagInventory(true, (byte) 0, (byte) 0);
		if (tagHandlers == null || tagHandlers.isEmpty()) {
			throw new Exception("No RFID tags detected in reader field");
		}

		ThBase matchedHandler = null;
		for (ThBase th : tagHandlers) {
			if (th != null) {
				String thUid = th.iddToHexString();
				String normThUid = thUid != null ? thUid.replaceAll("[^a-fA-F0-9]", "").toUpperCase() : "";
				if (targetUid.isEmpty() || targetUid.equals("*") || normThUid.equals(targetUid)) {
					matchedHandler = th;
					break;
				}
			}
		}

		if (matchedHandler == null) {
			for (ThBase th : tagHandlers) {
				if (th != null) {
					try { th.close(); } catch (Throwable ignored) {}
				}
			}
			throw new Exception("Tag with UID '" + uid + "' not found in reader field");
		}

		try {
			int actualBlockSize = blockSize > 0 ? blockSize : 4;
			int blockCount = (data.length + actualBlockSize - 1) / actualBlockSize;
			byte[] paddedData = new byte[blockCount * actualBlockSize];
			System.arraycopy(data, 0, paddedData, 0, data.length);

			DataBuffer db = new DataBuffer(paddedData);
			int res = matchedHandler.writeMultipleBlocks(0, blockCount, actualBlockSize, db);
			if (res != 0) {
				// Fallback: write block-by-block if bulk write failed
				for (int b = 0; b < blockCount; b++) {
					byte[] singleBlock = new byte[actualBlockSize];
					System.arraycopy(paddedData, b * actualBlockSize, singleBlock, 0, actualBlockSize);
					DataBuffer singleDb = new DataBuffer(singleBlock);
					int singleRes = matchedHandler.writeMultipleBlocks(b, 1, actualBlockSize, singleDb);
					if (singleRes != 0) {
						String errMsg = (feig != null)
								? " (FEIG Error: " + feig.getLastError() + ")"
								: "";
						throw new Exception("Failed to write block " + b + " (status: " + singleRes + errMsg + ")");
					}
					Thread.sleep(20);
				}
			}
			Thread.sleep(100);
		} finally {
			for (ThBase th : tagHandlers) {
				if (th != null) {
					try { th.close(); } catch (Throwable ignored) {}
				}
			}
		}
	}

	@Override
	public void writeTag(String uid, org.objectspace.rfid.FinnishDataModel model, Byte afi) throws Exception {
		if (model == null) {
			throw new IllegalArgumentException("FinnishDataModel cannot be null");
		}
		byte[] blockData = model.getBlock(0);
		writeTag(uid, blockData, 4);
		if (afi != null) {
			writeAfi(uid, afi);
		}
	}

	@Override
	public void writeAfi(String uid, byte afi) throws Exception {
		if (!isConnected()) {
			throw new Exception("RFID reader is not connected");
		}

		String targetUid = uid != null ? uid.replaceAll("[^a-fA-F0-9]", "").toUpperCase() : "";

		List<ThBase> tagHandlers = feig.tagInventory(true, (byte) 0, (byte) 0);
		if (tagHandlers == null || tagHandlers.isEmpty()) {
			throw new Exception("No RFID tags detected in reader field");
		}

		ThBase matchedHandler = null;
		for (ThBase th : tagHandlers) {
			if (th != null) {
				String thUid = th.iddToHexString();
				String normThUid = thUid != null ? thUid.replaceAll("[^a-fA-F0-9]", "").toUpperCase() : "";
				if (targetUid.isEmpty() || targetUid.equals("*") || normThUid.equals(targetUid)) {
					matchedHandler = th;
					break;
				}
			}
		}

		if (matchedHandler == null) {
			for (ThBase th : tagHandlers) {
				if (th != null) {
					try { th.close(); } catch (Throwable ignored) {}
				}
			}
			throw new Exception("Tag with UID '" + uid + "' not found in reader field");
		}

		try {
			if (matchedHandler instanceof ThIso15693) {
				int res = ((ThIso15693) matchedHandler).writeAFI(afi);
				if (res != 0) {
					String errMsg = (feig != null)
							? " (FEIG Error: " + feig.getLastError() + ")"
							: "";
					throw new Exception("Failed to write AFI byte 0x" + String.format("%02X", afi) + " (status: " + res + errMsg + ")");
				}
			} else {
				throw new Exception("Tag handler does not support ISO15693 AFI operations (" + matchedHandler.getClass().getName() + ")");
			}
			Thread.sleep(100);
		} finally {
			for (ThBase th : tagHandlers) {
				if (th != null) {
					try { th.close(); } catch (Throwable ignored) {}
				}
			}
		}
	}

	public FeigRFID getFeigRFID() {
		return feig;
	}

	private FeigRFID feig;
	private AbstractConfiguration config;
	protected HashMap<String, Integer> maxBlocksMap;
}

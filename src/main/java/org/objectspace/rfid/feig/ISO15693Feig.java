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

		List<ThBase> tagHandlers = feig.tagInventory(true, (byte) 0, (byte) 1);
		int totalTags = tagHandlers != null ? tagHandlers.size() : 0;
		if (totalTags == 0) {
			inventoryCallback.empty();
			return;
		}

		int counter = 0;
		for (ThBase tagHandler : tagHandlers) {
			try {
				if (tagHandler instanceof ThIso15693) {
					ThIso15693 th = (ThIso15693) tagHandler;

					// get size of tag from configuration or guess something
					String tagName = th.transponderName();
					Integer maxBlocks = maxBlocksMap.get(tagName);
					// tagName not in configuration
					if (maxBlocks == null) {
						maxBlocks = numBlocks;
						maxBlocksMap.put(tagName, maxBlocks);
					}

					// paranoia don't read too much
					int blocksToRead = Math.min(numBlocks, maxBlocks);

					// read data blocks
					LongRef blockSizeRef = new LongRef();
					DataBuffer dataBuffer = new DataBuffer();
					int back = th.readMultipleBlocks(0, blocksToRead, blockSizeRef, dataBuffer);
					byte[] data = dataBuffer.data();
					if (data == null || data.length == 0) {
						continue;
					}

					byte[] newBlock = inventoryCallback.doIt(
						counter,
						totalTags,
						th.manufacturerName(),
						tagName,
						th.iddToHexString(),
						data,
						blocksToRead
					);
					if (newBlock != null) {
					}
				} else {
					System.out.println("Tag not supported: " + tagHandler.getClass().getName());
				}
			} finally {
				try {
					tagHandler.close();
				} catch (Throwable t) {
					// Ignore
				}
			}
			counter++;
		}
	}
	private FeigRFID feig;
	private AbstractConfiguration config;
	protected HashMap<String, Integer> maxBlocksMap;
}

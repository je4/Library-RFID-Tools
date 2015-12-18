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
package org.objectspace.rfid.elatec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;

import de.feig.TagHandler.FedmIscTagHandler;
import de.feig.TagHandler.FedmIscTagHandler_ISO15693;
import de.feig.TagHandler.FedmIscTagHandler_Result;

/**
 * @author Juergen Enge
 *
 */
public class ISO15693Elatec implements ISO15693Reader {

	/**
	 * 
	 */
	public ISO15693Elatec(AbstractConfiguration config) {
		this.config = config;
		elatec = new ElatecRFID(config);
		readattempts = config.getInt("device.elatec.readattempts", 2);
		writeattempts = config.getInt("device.elatec.writeattempts", 3);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.library.ISO156893Reader#connect()
	 */
	public void connect() throws Exception {
		elatec.connect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.library.ISO156893Reader#init()
	 */
	public void init() throws Exception {
		elatec.SetTagTypes(0,  4);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.objectspace.rfid.library.ISO156893Reader#inventory(org.objectspace.
	 * rfid.TagCallback, int)
	 */
	public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
		int back = 0;

		ElatecTag tag = elatec.SearchTag();

		if (tag == null || !tag.result) {
			inventoryCallback.empty();
			return;
		}
		String tagName = ElatecTag.getTagString(tag.tagType);
		if (tag.tagType != ElatecTag.HFTAG_ISO15693) {
			System.out.println("Invalid tag type: " + tagName);
			inventoryCallback.empty();
			return;
		}

		// get size of tag from configuration or guess something
		Integer maxBlocks = maxBlocksMap.get(tagName);
		// tagName not in configuration
		if (maxBlocks == null) {
			maxBlocks = numBlocks;
			maxBlocksMap.put(tagName, maxBlocks);
		}

		// paranoia don't read too much
		numBlocks = Math.min(numBlocks, maxBlocks);

		byte[] buffer = new byte[numBlocks * 4];
		try {
			for (int i = 0; i < numBlocks; i++) {
				byte[] result = null;
				for (int j = 0; j < readattempts; j++) {
					result = elatec.ISO15693_ReadSingleBlock(i, (byte) (numBlocks * 4));
					Thread.sleep(20);
					if (result != null)
						break;
					Thread.sleep(300);
				}
				if (result == null) {
					buffer = null;
					System.out.println("Error reading tag " + ElatecRFID.bytesToHex(tag.id));
					break;
				}
				System.arraycopy(result, 0, buffer, i * 4, 4);
			}
		} catch (ElatecException e) {
			System.out.println("error reading tag: " + e.getMessage());
			buffer = null;
		}
		if (buffer != null) {
			byte[] newBlock = inventoryCallback.doIt(0, 1, "unknown", tagName, ElatecRFID.bytesToHex(tag.id), buffer,
					numBlocks);
			if (newBlock != null) {
				for (int i = 0; i < numBlocks; i++) {
					boolean ok = false;
					for (int j = 0; j < writeattempts; j++) {
						try {
						ok = elatec.ISO15693_WriteSingleBlock(i, Arrays.copyOfRange(newBlock, i * 4, i * 4 + 4));
						Thread.sleep(50);
						if (ok)
							break;
						} catch( ArrayIndexOutOfBoundsException e ) {
							System.out.println( "i: " + i);
							e.printStackTrace();
						}
						Thread.sleep(500);
					}
					if (!ok) {
						buffer = null;
						System.out.println("Error writing tag " + ElatecRFID.bytesToHex(tag.id));
						break;
					}
				}
				// back = th.writeMultipleBlocks(0, numBlocks,
				// res.blockSize,
				// newBlock);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.library.ISO156893Reader#close()
	 */
	public void close() throws Exception {
	}

	private AbstractConfiguration config;
	private ElatecRFID elatec = null;
	private int readattempts = 0;
	private int writeattempts = 0;
	protected HashMap<String, Integer> maxBlocksMap;
}

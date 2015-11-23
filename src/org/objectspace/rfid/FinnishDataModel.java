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
package org.objectspace.rfid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration2.AbstractConfiguration;

import de.feig.FeHexConvert;

/**
 * Representing the first record of the "Finnish Data Model"
 * 
 * @author Juergen Enge ({@link mailto:rfid@objectspace.org})
 * @todo adapt to ISO 28560-2
 */
public class FinnishDataModel {

	private static boolean DEBUG = false;
	public static String[] stringFieldNames = { "PrimaryItemId", "CountryOfOwnerLib", "ISIL" };

	/**
	 * Default constructor
	 */
	public FinnishDataModel() {
	}

	/**
	 * parse a data block and set internal variables
	 * @param byte[] data the data block must contain a multiple of blockSize
	 * @param long blockSize atomic size of smallest block (depends on hardware tag)  
	 * @throws Exception
	 */
	public void setBlock(byte[] data, long blockSize) throws Exception {
		assert data.length % blockSize == 0 : data.length >= 32;

		// check, whether block is empty (only zero values)
		boolean notEmpty = false;
		for (int i = 0; i < data.length; i++)
			notEmpty |= (data[i] != 0x00);
		isEmpty = !notEmpty;

		// extract version (first 4 bit)
		version = data[0] >> 4;
		/*
		 * if (version != 1 && version != 2) { version = 0; throw new Exception(
		 * "Version <> 1"); }
		 */
		// typeOfUsage (bit 4 to 7)
		typeOfUsage = data[0] & 0x0f;
		// 2nd byte
		partsInItem = data[1];
		// 3rd byte
		partNumber = data[2];
		
		// byte 3-18 primaryItemId
		primaryItemId = new String(Arrays.copyOfRange(data, 3, 3 + 15)).trim();
		// byte 19/29
		crcOrig = new byte[] { data[19], data[20] };
		// byte 21/22 is the country part of ISIL
		countryOfOwnerLib = new String(Arrays.copyOfRange(data, 21, 21 + 2)).trim();
		// byte 23-33(35) ISIL
		ISIL = new String(Arrays.copyOfRange(data, 23, Math.min(data.length, 36) - 1)).trim();

		// create CRC
		crc = TagCRC(data);

		// compare crc with original crc
		// Binary encoding with the lsb stored at the lowest memory location
		crcbytes = new byte[] { (byte) (crc & 0xFF), (byte) (crc >> 8 & 0xFF) };
		crcError = (crcOrig[0] != crcbytes[0] || crcOrig[1] != crcbytes[1]);

		if (DEBUG) {
			System.out.println("Version: " + version);
			System.out.println("Type of usage: " + typeOfUsage);
			System.out.println("Parts in item: " + partsInItem);
			System.out.println("Part number: " + partNumber);
			System.out.println("Primary item ID: " + primaryItemId);
			System.out.println("CRC (lsb): " + FeHexConvert.byteArrayToHexString(crcbytes));
			System.out.println("Country of owner library: " + countryOfOwnerLib);
			System.out.println("ISIL: " + ISIL);
		}
	}

	/**
	 * set internal values 
	 * @param typeOfUsage
	 * @param partsInItem
	 * @param partNumber
	 * @param primaryItemId
	 * @param countryOfOwnerLib
	 * @param ISIL
	 */
	public void setValues(int typeOfUsage, int partsInItem, int partNumber, String primaryItemId,
			String countryOfOwnerLib, String ISIL) {
		this.version = 1;
		this.typeOfUsage = typeOfUsage;
		this.partsInItem = partsInItem;
		this.partNumber = partNumber;
		this.primaryItemId = primaryItemId;
		this.countryOfOwnerLib = countryOfOwnerLib;
		this.ISIL = ISIL;
	}

	/**
	 * Execute regex Expressions on primaryItemId, CountryOfOwnerLib and ISIL
	 * @param regex map with regex
	 * @return true, if data has been changed
	 */
	public boolean doRegex(HashMap<String, FinnishDataModelRegex> regex) {
		dataChanged = false;
		for (Map.Entry<String, FinnishDataModelRegex> e : regex.entrySet()) {
			String result = null;
			FinnishDataModelRegex fdmr = e.getValue();
			String fld = e.getKey();
			switch (fld) {
			case "PrimaryItemId":
				result = fdmr.replace(this, fld);
				if (!result.equals(primaryItemId)) {
					primaryItemId = result;
					dataChanged = true;
				}
				break;
			case "CountryOfOwnerLib":
				result = fdmr.replace(this, fld);
				if (!result.equals(countryOfOwnerLib)) {
					countryOfOwnerLib = result;
					dataChanged = true;
				}
				break;
			case "ISIL":
				result = fdmr.replace(this, fld);
				if (!result.equals(ISIL)) {
					ISIL = result;
					dataChanged = true;
				}
				break;
			default:
			}
		}
		return dataChanged;
	}

	/**
	 * creates an empty data block (zero values)
	 * @param size
	 * @return data block with zero values
	 */
	static public byte[] getEmptyBlock(int size) {
		byte[] block = new byte[size];
		Arrays.fill(block, (byte) 0);
		return block;
	}

	/**
	 * builds a data block containing the actual values
	 * @param size
	 * @return
	 */
	public byte[] getBlock(int size) {
		byte[] block = getEmptyBlock( size );
		block[0] = (byte) ((version << 4) | typeOfUsage);
		block[1] = (byte) partsInItem;
		block[2] = (byte) partNumber;

		byte[] temp = primaryItemId.getBytes();
		for (int i = 3; i <= 3 + 15; i++) {
			if (i - 3 < temp.length) {
				block[i] = temp[i - 3];
			}
		}
		temp = countryOfOwnerLib.getBytes();
		for (int i = 21; i <= 21 + 2; i++) {
			if (i - 21 < temp.length) {
				block[i] = temp[i - 21];
			}
		}
		temp = ISIL.getBytes();
		for (int i = 23; i < block.length; i++) {
			if (i - 23 < temp.length) {
				block[i] = temp[i - 23];
			}
		}

		int crc = TagCRC(block);
		// Binary encoding with the lsb stored at the lowest memory location
		crcbytes = new byte[] { (byte) (crc & 0xFF), (byte) (crc >> 8 & 0xFF) };
		block[19] = crcbytes[0];
		block[20] = crcbytes[1];

		return block;
	}

	/**
	 * creates crc checksum of data block (excluding byte 19/20)
	 * @param data
	 * @return
	 */
	protected static int TagCRC(byte[] data) {
		// create a byte array without crc bytes
		byte[] crcdata = new byte[data.length];
		System.arraycopy(data, 0, crcdata, 0, 19);
		System.arraycopy(data, 21, crcdata, 19, data.length - 21);
		int p = 19 + data.length - 21;
		while (p < 32) {
			crcdata[p] = 0x00;
			p++;
		}

		return CRC16CCITT(crcdata, 0, 32);
	}

	/**
	 * creates a crc16 checksum of a data block
	 * @param data the data block
	 * @param start start byte
	 * @param length number of bytes to use
	 * @return
	 */
	protected static int CRC16CCITT(byte[] data, int start, int length) {
		int crc = 0xFFFF; // initial value
		int polynomial = 0x1021; // 0001 0000 0010 0001 (0, 5, 12)

		// byte[] testBytes = "123456789".getBytes("ASCII");

		for (int p = start; p < start + length; p++) {
			for (int i = 0; i < 8; i++) {
				boolean bit = ((data[p] >> (7 - i) & 1) == 1);
				boolean c15 = ((crc >> 15 & 1) == 1);
				crc <<= 1;
				if (c15 ^ bit)
					crc ^= polynomial;
			}
		}

		crc &= 0xffff;
		return crc;
		// System.out.println("CRC16-CCITT = " + Integer.toHexString(crc));
	}

	/**
	 * getter
	 * @return primaryItemId
	 */
	public String getPrimaryItemId() {
		return this.primaryItemId;
	}

	/**
	 * getter
	 * @return countryOfOwnerLib
	 */
	public String getCountryOfOwnerLib() {
		return this.countryOfOwnerLib;
	}

	/**
	 * getter
	 * @return ISIL
	 */
	public String getISIL() {
		return this.ISIL;
	}

	/**
	 * getter
	 * @return typeOfUsage
	 */
	public int getTypeOfUsage() {
		return this.typeOfUsage;
	}

	/**
	 * getter
	 * @return partsInItem
	 */
	public int getPartsInItem() {
		return this.partsInItem;
	}

	/**
	 * getter
	 * @return partNumber
	 */
	public int getPartNumber() {
		return this.partNumber;
	}

	/**
	 * getter
	 * @return version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * getter for crc (integer)
	 * @return crc (actual)
	 */
	public int getCRC() {
		return crc;
	}

	/**
	 * getter for crc (byte[])
	 * @return crc (actual)
	 */
	public byte[] getCRCBytes() {
		return crcbytes;
	}

	/**
	 * getter
	 * @return original crc
	 */
	public byte[] getCRCOrigBytes() {
		return crcOrig;
	}
	
	/**
	 * check for crc error
	 * @return true, if crc is not correct
	 */
	public boolean getCRCError() {
		return crcError;
	}

	/**
	 * check for empty tag
	 * @return true if empty
	 */
	public boolean isEmpty() {
		return isEmpty;
	}

	/**
	 * get value by field name
	 * @param field name
	 * @return field value
	 */
	public String getStringValue(String field) {
		switch (field) {
		case "PrimaryItemId":
			return primaryItemId;
		case "CountryOfOwnerLib":
			return countryOfOwnerLib;
		case "ISIL":
			return ISIL;
		default:
			return null;
		}
	}

	protected AbstractConfiguration config;
	protected String primaryItemId = null;
	protected String countryOfOwnerLib = null;
	protected String ISIL = null;
	protected int typeOfUsage = 0;
	protected int partsInItem = 0;
	protected int partNumber = 0;
	protected int crc = 0;
	protected int version = 0;
	protected byte[] crcbytes = null;
	protected byte[] crcOrig;
	protected boolean crcError = false;
	protected boolean isEmpty = false;
	protected boolean dataChanged = false;

}

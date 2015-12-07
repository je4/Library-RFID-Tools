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

/**
 * representation of an optional block
 * 
 * @author Juergen Enge
 *
 */
public class FinnishDataModelOptionalBlock {

	/**
	 * default constructor
	 */
	public FinnishDataModelOptionalBlock() {
	}

	/**
	 * read optional data from raw block
	 * 
	 * @param data
	 *            raw data block
	 * @param start
	 *            start of optional data block
	 * @return position of next block
	 * @throws Exception 
	 */
	public int setBlock(byte[] raw, int start) throws Exception {
		assert raw.length > start + 1;

		// empty block?
		if (raw[start] == 0x00)
			return 0;

		long length = raw[start];
		if (length == 1) {
			throw new Exception("i do not know how to handle filler block");
		}

		int rawStart;
		long rawLength;
		if (raw[start + 2] == 0xff) {
			id = ((long) raw[start + 4]) << 16 + ((long) raw[start + 3]) << 8 + ((long) raw[start + 1]);
			rawStart = start + 5;
			rawLength = length - 5 - 1;
		} else {
			id = (((long) raw[start + 2]) << 8) + ((long) raw[start + 1]);
			rawStart = start + 3;
			rawLength = length - 3 - 1;
		}
		if (raw.length < rawStart + rawLength + 1)
			throw new Exception("optional raw block corrupt");
		data = Arrays.copyOfRange(raw, rawStart, (int) Math.min(raw.length, rawStart + rawLength));

		// checksum of optional block is xor of block including checksum (0)
		byte checksum = 0x00;
		for (int i = start; i < Math.min(raw.length, start + length - 1); i++) {
			checksum ^= raw[i];
		}
		checksum ^= 0;
		xor = checksum;
		origXOR = raw[(int) (start + length - 1)];

		return (int) (start + length);

	}
	/**
	 * set optional data
	 * @param id block id
	 * @param data raw block data
	 */
	public void setData(long id, byte[] data ) {
		xor = 0x00;
		origXOR = 0x00;
		this.id = id;
		this.data = data;
	}

	/**
	 * create data block
	 * @return data block
	 */
	public byte[] getBlock() {
		int length = 4 + data.length;
		int st;
		if( id > 0xffff ) length += 2;
		byte[] d = new byte[length];
		d[0] = (byte) length;
		d[1] = (byte) (id & 0xff);
		if( id >= 0xffff ) {
			d[2] = (byte) 0xff;
			d[3] = (byte) ((id >> 8) & 0xff);
			d[4] = (byte) ((id >> 16) & 0xff);
			st = 5;
		}
		else {
			d[2] = (byte) ((id >> 8) & 0xff);
			st = 3;
		}
		for( int i = 0; i < data.length; i++ ) {
			d[st+i] = data[i];
		}
		xor = 0x00;
		for( int i = 0; i < st + data.length; i++ ) {
			xor ^= d[i];
		}
		xor ^= 0x00;
		d[st + data.length] = xor;
		return d;
	}
	
	/**
	 * getter for raw data
	 * @return raw data of optional block
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * check for xor error
	 * @return true, if xor checksum has an error
	 */
	public boolean xorError() {
		return xor != origXOR;
	}
	
	/**
	 * get optional block id
	 * @return block id
	 */
	public long getID() {
		return id;
	}

	/**
	 * get computed XOR
	 * @return computed XOR
	 */
	public byte getXOR() {
		// TODO Auto-generated method stub
		return xor;
	}
	
	private byte[] data = null;
	private long id = 0;
	private byte xor = 0;
	private byte origXOR = 0;
}

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

import java.math.BigInteger;

/**
 * @author Juergen Enge
 *
 */
public class ElatecTag {

	public static final byte NOTAG = 0x00;
	// LF Tags
	public static final byte LFTAG_EM4102 = 0x40; // "EM4x02/CASI-RUSCO"
													// (=IDRO_A)
	public static final byte LFTAG_HITAG1S = 0x41; // "HITAG 1/HITAG S"
													// (=IDRW_B)
	public static final byte LFTAG_HITAG2 = 0x42; // "HITAG 2" (=IDRW_C)
	public static final byte LFTAG_EM4150 = 0x43; // "EM4x50" (=IDRW_D)
	public static final byte LFTAG_AT5555 = 0x44; // "T55x7" (=IDRW_E)
	public static final byte LFTAG_ISOFDX = 0x45; // "ISO FDX-B" (=IDRO_G)
	public static final byte LFTAG_EM4026 = 0x46; // N/A (=IDRO_H)
	public static final byte LFTAG_HITAGU = 0x47; // N/A (=IDRW_I)
	public static final byte LFTAG_EM4305 = 0x48; // N/A (=IDRW_K)
	public static final byte LFTAG_HIDPROX = 0x49; // "HID Prox"
	public static final byte LFTAG_TIRIS = 0x4A; // "ISO HDX/TIRIS"
	public static final byte LFTAG_COTAG = 0x4B; // "Cotag"
	public static final byte LFTAG_IOPROX = 0x4C; // "ioProx"
	public static final byte LFTAG_INDITAG = 0x4D; // "Indala"
	public static final byte LFTAG_HONEYTAG = 0x4E; // "NexWatch"
	public static final byte LFTAG_AWID = 0x4F; // "AWID"
	public static final byte LFTAG_GPROX = 0x50; // "G-Prox"
	public static final byte LFTAG_PYRAMID = 0x51; // "Pyramid"
	public static final byte LFTAG_KERI = 0x52; // "Keri"
	public static final byte LFTAG_DEISTER = 0x53; // N/A
	// HF Tags
	public static final byte HFTAG_MIFARE = (byte) 0x80; // "ISO14443A/MIFARE"
	public static final byte HFTAG_ISO14443B = (byte) 0x81; // "ISO14443B"
	public static final byte HFTAG_ISO15693 = (byte) 0x82; // "ISO15693"
	public static final byte HFTAG_LEGIC = (byte) 0x83; // "LEGIC"
	public static final byte HFTAG_HIDICLASS = (byte) 0x84; // "HID iCLASS"
	public static final byte HFTAG_FELICA = (byte) 0x85; // "FeliCa"
	public static final byte HFTAG_SRX = (byte) 0x86; // "SRX"
	public static final byte HFTAG_NFCP2P = (byte) 0x87; // "NFC Peer-to-Peer"

	/**
	 * 
	 */
	public ElatecTag(byte[] data) {
		String hex = ElatecRFID.bytesToHex(data);
		if (data.length < 4)
			return;
		result = data[0] != 0;
		if (result) {
			tagType = data[1];
			idBitCount = data[2];
			byte idSize = data[3];
			if (idSize < 1) {
				result = false;
			} else {
				id = new byte[idSize];
				for (int i = 0; i < idSize; i++)
					id[i] = data[4 + i];
			}
		}
	}

	static public String getTagString(byte tagType) {
		switch (tagType) {
		case NOTAG:
			return "no tag";
		// LF Tags
		case LFTAG_EM4102:
			return "EM4x02/CASI-RUSCO";
		case LFTAG_HITAG1S:
			return "HITAG 1/HITAG S";
		case LFTAG_HITAG2:
			return "HITAG 2";
		case LFTAG_EM4150:
			return "EM4x50";
		case LFTAG_AT5555:
			return "T55x7";
		case LFTAG_ISOFDX:
			return "ISO FDX-B";
		case LFTAG_EM4026:
			return "N/A (LFTAG_EM4026)";
		case LFTAG_HITAGU:
			return "N/A (LFTAG_HITAGU)";
		case LFTAG_EM4305:
			return "N/A (LFTAG_EM4305)";
		case LFTAG_HIDPROX:
			return "HID Prox";
		case LFTAG_TIRIS:
			return "ISO HDX/TIRIS";
		case LFTAG_COTAG:
			return "Cotag";
		case LFTAG_IOPROX:
			return "ioProx";
		case LFTAG_INDITAG:
			return "Indala";
		case LFTAG_HONEYTAG:
			return "NexWatch";
		case LFTAG_AWID:
			return "AWID";
		case LFTAG_GPROX:
			return "G-Prox";
		case LFTAG_PYRAMID:
			return "Pyramid";
		case LFTAG_KERI:
			return "Keri";
		case LFTAG_DEISTER:
			return "N/A (LFTAG_DEISTER)";
		// HF Tags
		case HFTAG_MIFARE:
			return "ISO14443A/MIFARE";
		case HFTAG_ISO14443B:
			return "ISO14443B";
		case HFTAG_ISO15693:
			return "ISO15693";
		case HFTAG_LEGIC:
			return "LEGIC";
		case HFTAG_HIDICLASS:
			return "HID iCLASS";
		case HFTAG_FELICA:
			return "FeliCa";
		case HFTAG_SRX:
			return "SRX";
		case HFTAG_NFCP2P:
			return "NFC Peer-to-Peer";
		default:
			return String.format("unknown tag (%02X)", tagType);
		}
	}

	public String getString() {
		if (!result)
			return "no tag";
		return String.format("TagType: 0x%02X %s // IDBitCount: %d // ID: %s", tagType, getTagString(tagType),
				idBitCount, ElatecRFID.bytesToHex(id));
	}

	public boolean result = false;
	public byte tagType = 0;
	public byte idBitCount = 0;
	public byte[] id = null;
}

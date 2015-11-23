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

import java.util.Arrays;

/**
 * @author Juergen Enge
 *
 */
public class ElatecDESFireKeySettings {

	/**
	 * 
	 */
	public ElatecDESFireKeySettings(byte[] data) {
		KeySettings = data[0];
		NumberOfKeys = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 1, 5));
		KeyType = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 6, 10));
	}

	static public String getKeyTypeString(int KeyType ) {
		switch( KeyType ) {
		case ElatecRFID.DESF_KEYTYPE_3DES:
			return "DESF_KEYTYPE_3DES";
		case ElatecRFID.DESF_KEYTYPE_3K3DES:
			return "DESF_KEYTYPE_3K3DES";
		case ElatecRFID.DESF_KEYTYPE_AES:
			return "DESF_KEYTYPE_3K3DES";
		default:
			return "unknown keytype";
		}
	}
	
	public byte KeySettings;
	public long NumberOfKeys;
	public long KeyType;
}

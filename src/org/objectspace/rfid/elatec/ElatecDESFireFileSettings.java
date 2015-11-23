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

/*
typedef struct __attribute__((__packed__))
{
	byte FileType;
	byte CommSet;
	uint16_t AccessRights;
	union TDESFireSpecificFileInfo
	{
		struct TDESFireDataFileSettings
		{
			uint32_t FileSize;
		} DataFileSettings;
		struct TDESFireValueFileSettings
		{
			uint32_t LowerLimit;
			uint32_t UpperLimit;
			uint32_t LimitedCreditValue;
			bool LimitedCreditEnabled;
		} ValueFileSettings;
		struct TDESFireRecordFileSettings
		{
			uint32_t RecordSize;
			uint32_t MaxNumberOfRecords;
			uint32_t CurrentNumberOfRecords;
		} RecordFileSettings;
	}	SpecificFileInfo;
} TDESFireFileSettings;
 */

/**
 * @author Juergen Enge
 *
 */
public class ElatecDESFireFileSettings {

	/**
	 * 
	 */
	public ElatecDESFireFileSettings(long AID, byte fileID, byte[] data) {
		this.AID = AID;
		this.fileID = fileID;
		fileType = data[0];
		commSet = data[1];
		accessRights = ElatecRFID.LSBBytesToInt(Arrays.copyOfRange(data, 2, 2 + 2));
		accessRead = (byte) ((accessRights >> 12) & 0x0F);
		accessWrite = (byte) ((accessRights >> 8) & 0x0F);
		accessReadWrite = (byte) ((accessRights >> 4) & 0x0F);
		accessChange = (byte) ((accessRights >> 0) & 0x0F);
		
		switch (fileType) {
		case ElatecRFID.DESF_FILETYPE_STDDATAFILE:
		case ElatecRFID.DESF_FILETYPE_BACKUPDATAFILE:
			dataFileSize = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 4, 4 + 4));
			break;
		case ElatecRFID.DESF_FILETYPE_VALUEFILE:
			valueLowerLimit = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 4, 4 + 4));
			valueUpperLimit = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 8, 8 + 4));
			valueLimitedCreditValue = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 12, 12 + 4));
			valueLimitedCreditEnabled = (data[16] > 0);
			break;
		case ElatecRFID.DESF_FILETYPE_CYCLICRECORDFILE:
		case ElatecRFID.DESF_FILETYPE_LINEARRECORDFILE:
			recordSize = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 4, 4 + 4));
			recordMaxNumberOfRecords = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 8, 8 + 4));
			recordCurrentNumberOfRecords = ElatecRFID.LSBBytesToLong(Arrays.copyOfRange(data, 12, 12 + 4));
			break;
		}
	}

	public String toString() {
		String str = String.format("%s:%02X // FileType: %d (%s) // CommSet: %d (%s) // AccessRights: %s", ElatecRFID.longToHex(AID),
				fileID, fileType, getFileTypeString(fileType), commSet, getCommSetString(commSet), ElatecRFID.intToHex(accessRights));
		switch (fileType) {
		case ElatecRFID.DESF_FILETYPE_STDDATAFILE:
		case ElatecRFID.DESF_FILETYPE_BACKUPDATAFILE:
			str += String.format(" // FileSize: %d", dataFileSize);
			break;
		case ElatecRFID.DESF_FILETYPE_VALUEFILE:
			str += String.format(" // LowerLimit: %d", valueLowerLimit);
			str += String.format(" // UpperLimit: %d", valueUpperLimit);
			str += String.format(" // LimitedCreditValue: %d", valueLimitedCreditValue);
			str += String.format(" // LimitedCreditEnable: %s", valueLimitedCreditEnabled ? "true" : "false");
			break;
		case ElatecRFID.DESF_FILETYPE_CYCLICRECORDFILE:
		case ElatecRFID.DESF_FILETYPE_LINEARRECORDFILE:
			str += String.format(" // RecordSize: %d", recordSize);
			str += String.format(" // MaxNumberOfRecords: %d", recordMaxNumberOfRecords);
			str += String.format(" // CurrentNumberOfRecords: %d", recordCurrentNumberOfRecords);
			break;
		}
		return str;
	}

	static public String getFileTypeString(byte fileType) {
		switch (fileType) {
		case ElatecRFID.DESF_FILETYPE_STDDATAFILE:
			return "DESF_FILETYPE_STDDATAFILE";
		case ElatecRFID.DESF_FILETYPE_BACKUPDATAFILE:
			return "DESF_FILETYPE_BACKUPDATAFILE";
		case ElatecRFID.DESF_FILETYPE_VALUEFILE:
			return "DESF_FILETYPE_VALUEFILE";
		case ElatecRFID.DESF_FILETYPE_LINEARRECORDFILE:
			return "DESF_FILETYPE_LINEARRECORDFILE";
		case ElatecRFID.DESF_FILETYPE_CYCLICRECORDFILE:
			return "DESF_FILETYPE_CYCLICRECORDFILE";
		default:
			return "unknown filetype";
		}
	}

	static public String getCommSetString(byte commSet) {
		switch (commSet) {
		case ElatecRFID.DESF_COMMSET_PLAIN:
			return "DESF_COMMSET_PLAIN";
		case ElatecRFID.DESF_COMMSET_PLAIN_MACED:
			return "DESF_COMMSET_PLAIN_MACED";
		case ElatecRFID.DESF_COMMSET_FULLY_ENC:
			return "DESF_COMMSET_FULLY_ENC";
		default:
			return "unknown commset";
		}
	}

	public long AID;
	public byte fileID;
	public byte fileType;
	public byte commSet;
	public int accessRights;
	public byte accessRead;
	public byte accessWrite;
	public byte accessReadWrite;
	public byte accessChange;	
	public long dataFileSize;
	public long valueLowerLimit;
	public long valueUpperLimit;
	public long valueLimitedCreditValue;
	public boolean valueLimitedCreditEnabled;
	public long recordSize;
	public long recordMaxNumberOfRecords;
	public long recordCurrentNumberOfRecords;

}

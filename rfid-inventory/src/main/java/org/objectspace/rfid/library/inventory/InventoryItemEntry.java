/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
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
 *******************************************************************************/
package org.objectspace.rfid.library.inventory;

/**
 * Data model representing a single scanned RFID item in the inventory list.
 */
public class InventoryItemEntry {
	public boolean statusOk = true;
	public String statusSymbol = "\u2714";
	public String statusDetails = "OK";
	public int index;
	public String time;
	public String primaryItemId;
	public String signature;
	public String partInfo;
	public String isilAndCountry;
	public int usageType;
	public String marker;
	public String uid;
	public String crcStatus;
	public String manufacturer;
	public String tagName;
	public byte[] rawData;
	public int version;
	public int partNumber;
	public int partsInItem;
	public String country;
	public String isil;
}

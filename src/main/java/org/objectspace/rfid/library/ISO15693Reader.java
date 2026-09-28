/*******************************************************************************
 * Copyright 2015
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/G�ttingen
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
 * HAWK RFID Library Tools ist Freie Software: Sie k�nnen es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * ver�ffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es n�tzlich sein wird, aber
 * OHNE JEDE GEW�HRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gew�hrleistung der MARKTF�HIGKEIT oder EIGNUNG F�R EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License f�r weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.library;

import org.objectspace.rfid.TagCallback;

/**
 * interface for ISO15693 tag compliant reader
 * @author Juergen Enge
 *
 */
public interface ISO15693Reader {
	/**
	 * connect hardware device
	 * @throws Exception
	 */
	public void connect() throws Exception;
	
	/**
	 * initialize hardware device
	 * @throws Exception
	 */
	public void init() throws Exception;

	/**
	 * execute an inventory and read data blocks
	 * @param inventoryCallback
	 * @param numBlocks number of blocks to read
	 * @throws Exception
	 */
	public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception;
	
	/**
	 * check if hardware device is currently connected
	 * @return true if connected
	 */
	public boolean isConnected();

	/**
	 * cleanup
	 * @throws Exception
	 */
	public void close() throws Exception;
}

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

/**
 * interface for a callback function, which is used by tag reader threads
 * @author Juergen Enge
 *
 */
public interface TagCallback {
	/**
	 * callback function for every read tag
	 * @param counter tag number (< elements)
	 * @param elements number of elements read at once
	 * @param manufacturerName name of tag manufacturer
	 * @param tagName name of tag (type)
	 * @param UID tag identifier
	 * @param data raw tag data
	 * @param blockSize minimum number of bytes, which can be read or written
	 * @return data block, if new data should be written onto the tag, null otherwise
	 * @throws Exception
	 */
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data, long blockSize)
			throws Exception;

	/**
	 * will be called if no tag is in reader distance
	 */
	public void empty();

	/**
	 * cleanup
	 * @throws Exception
	 */
	public void close() throws Exception;
}

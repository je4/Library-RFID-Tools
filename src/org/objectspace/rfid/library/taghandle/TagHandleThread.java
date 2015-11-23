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
package org.objectspace.rfid.library.taghandle;

import java.sql.SQLException;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.library.ISO15693Reader;

/**
 * @author Juergen Enge
 *
 */
public class TagHandleThread implements Runnable {

	/**
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * 
	 */
	public TagHandleThread(ISO15693Reader reader, AbstractConfiguration config, MainDialog mainDialog, WebCamThread wct)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this.reader = reader;
		inventoryCallback = new TagHandleInventory(config, mainDialog, wct);
		numBlocks = config.getInt("numblocks", 8);
		sleep = config.getInt( "taghandle.sleep", 500 );
	}

	@Override
	public synchronized void run() {

		try {
			while (running) {
				// read buffer
				try {
					inventory();
				} catch (Exception e) {
					e.printStackTrace();
				}

				Thread.sleep(sleep);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				inventoryCallback.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

	}

	private void inventory() throws Exception {
		reader.inventory(inventoryCallback, numBlocks);
	}

	public void dispose() {
		running = false;
	}

	private volatile boolean running = true;
	protected ISO15693Reader reader = null;
	protected TagHandleInventory inventoryCallback;
	protected int numBlocks = 0;
	protected int sleep = 500;

}

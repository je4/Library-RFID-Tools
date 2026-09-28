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
package org.objectspace.rfid.library.inventory;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;

/**
 * @author Juergen Enge
 *
 */
public class InventoryThread implements Runnable {

	/**
	 * 
	 */
	public InventoryThread(ISO15693Reader reader, InventoryCallback inventoryCallback, InventoryDialog id,
			AbstractConfiguration config) {
		this.reader = reader;
		this.inventoryCallback = inventoryCallback;
		this.config = config;
		this.id = id;
		numBlocks = config.getInt("numblocks", 8);
		sleep = config.getInt("inventory.sleep", 400);
	}

	protected boolean inventoryRunning() {
		inventoryRunning = false;
		if (!id.isDisposed()) {
			id.getDisplay().syncExec(new Runnable() {
				public void run() {
					inventoryRunning = id.isRunning();
				}
			});
		} else
			running = false;
		return inventoryRunning;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			while (running) {
				// read buffer
				try {
					if (!pause && reader != null) {
						if (!reader.isConnected()) {
							try {
								reader.connect();
								reader.init();
							} catch (Exception e) {
								// Device not connected yet
							}
						}
						if (reader.isConnected()) {
							reader.inventory(inventoryCallback, numBlocks);
						}
					}
				} catch (Exception e) {
					// Communication error during scan
				}
				Thread.sleep(sleep);
			}
		} catch (InterruptedException e) {
			// Thread interrupted
		} finally {
			try {
				inventoryCallback.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public void pause(boolean pause) {
		this.pause = pause;
		if (pause == false)
			inventoryCallback.clearUIDList();
	}

	public void dispose() {
		running = false;
	}

	private boolean running = true;
	private boolean inventoryRunning = false;
	protected ISO15693Reader reader = null;
	protected InventoryCallback inventoryCallback;
	private AbstractConfiguration config;
	protected int numBlocks = 0;
	protected InventoryDialog id = null;
	protected int sleep = 500;
	private boolean pause = true;

}

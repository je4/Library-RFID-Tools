/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * Based on HAWK RFID Library Tools:
 * Copyright 2015 Center for Information, Media and Technology (ZIMT),
 * HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
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
 * 
 * Diese Datei ist Teil von RFID Library Tools.
 *  
 * RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
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
package org.objectspace.rfid.library.inventory;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.library.ISO15693Reader;

/**
 * Background worker thread for RFID inventory and hardware monitoring.
 * Continuously polls hardware connection and executes tag scans.
 * 
 * @author Juergen Enge
 *
 */
public class InventoryThread implements Runnable {

	/**
	 * 
	 */
	public InventoryThread(ISO15693Reader reader, InventoryCallback inventoryCallback, InventoryView id,
			AbstractConfiguration config) {
		this.reader = reader;
		this.inventoryCallback = inventoryCallback;
		this.config = config;
		this.id = id;
		numBlocks = config.getInt("numblocks", 8);
		sleep = config.getInt("inventory.sleep", 400);
	}

	protected boolean inventoryRunning() {
		if (id != null && !id.isDisposed()) {
			return id.isRunning();
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Boolean lastReportedState = null;
		try {
			while (running) {
				boolean currentlyConnected = false;

				if (reader != null) {
					try {
						if (!reader.isConnected()) {
							// Attempt to connect if plugged in
							try {
								reader.connect();
								reader.init();
							} catch (Exception e) {
								// Device not plugged in yet
							}
						} else {
							// Check if still reachable
							reader.checkConnection();
						}
						currentlyConnected = reader.isConnected();
					} catch (Exception e) {
						currentlyConnected = false;
					}
				}

				// If connection state changed, notify UI immediately
				if (lastReportedState == null || lastReportedState.booleanValue() != currentlyConnected) {
					lastReportedState = Boolean.valueOf(currentlyConnected);
					String devInfo = (reader != null && currentlyConnected) ? reader.getDeviceInfo() : null;
					if (id != null && !id.isDisposed()) {
						id.onReaderConnectionChanged(currentlyConnected, devInfo);
					}
				}

				// Perform inventory scan if active and reader is connected
				if (!pause && currentlyConnected && reader != null) {
					String marker = (id != null && !id.isDisposed()) ? id.getTagInfo() : null;
					if (marker != null && !marker.trim().isEmpty()) {
						try {
							reader.inventory(inventoryCallback, numBlocks);
						} catch (Exception e) {
							// Error during scan (e.g., cable pulled or scan issue)
							System.err.println("Inventory scan error: " + e.getMessage());
							currentlyConnected = reader.isConnected();
							if (lastReportedState == null || lastReportedState.booleanValue() != currentlyConnected) {
								lastReportedState = Boolean.valueOf(currentlyConnected);
								if (id != null && !id.isDisposed()) {
									id.onReaderConnectionChanged(currentlyConnected, null);
								}
							}
						}
					}
				}

				Thread.sleep(sleep);
			}
		} catch (InterruptedException e) {
			// Thread interrupted
		} finally {
			try {
				if (inventoryCallback != null) {
					inventoryCallback.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public void pause(boolean pause) {
		if (!pause) {
			String marker = (id != null && !id.isDisposed()) ? id.getTagInfo() : null;
			if (marker == null || marker.trim().isEmpty()) {
				this.pause = true;
				return;
			}
		}
		this.pause = pause;
		if (pause == false && inventoryCallback != null)
			inventoryCallback.clearUIDList();
	}

	public boolean isPaused() {
		return pause;
	}

	public InventoryCallback getInventoryCallback() {
		return inventoryCallback;
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
	protected InventoryView id = null;
	protected int sleep = 500;
	private volatile boolean pause = true;

}

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

import org.objectspace.rfid.FinnishDataModel;

/**
 * Common abstraction for all RFID inventory UI frontends (SWT, Swing/FlatLaf, etc.).
 */
public interface InventoryView {

	/**
	 * Append raw text to the log / console view.
	 */
	void print(String text, int c1, int c2);

	/**
	 * Retrieve the currently configured location / marker string from the UI.
	 */
	String getTagInfo();

	/**
	 * Add an inventory item entry to the table and update KPI metrics.
	 */
	void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2);

	/**
	 * Add an inventory item entry with sync status to the table and update KPI metrics.
	 */
	void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2, boolean syncOk, String statusDetails);

	/**
	 * Notify the UI about reader hardware connection status changes.
	 */
	void onReaderConnectionChanged(boolean connected, String deviceInfo);

	/**
	 * Query whether scanning is currently active.
	 */
	boolean isRunning();

	/**
	 * Query whether the UI window/dialog has been closed or disposed.
	 */
	boolean isDisposed();

	/**
	 * Connect the callback to this view.
	 */
	void setCallback(InventoryCallback callback);

	/**
	 * Connect the scanning thread to this view.
	 */
	void setThread(InventoryThread thread);
}

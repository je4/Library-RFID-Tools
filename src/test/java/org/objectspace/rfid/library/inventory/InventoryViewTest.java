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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;

public class InventoryViewTest {

	// Test implementation of InventoryView for headless testing
	public static class MockInventoryView implements InventoryView {
		public final List<String> logs = new ArrayList<>();
		public final List<InventoryItemEntry> items = new ArrayList<>();
		public boolean connected = false;
		public String deviceInfo = null;
		public boolean running = false;
		public String marker = "Test-Shelf-A";
		public InventoryCallback callback;
		public InventoryThread thread;

		@Override
		public void print(String text, int c1, int c2) {
			logs.add(text);
		}

		@Override
		public String getTagInfo() {
			return marker;
		}

		@Override
		public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
				String manufacturer, String tagName, int c1, int c2) {
			addInventoryItem(uid, metadata, signature, marker, manufacturer, tagName, c1, c2, true, "OK");
		}

		@Override
		public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
				String manufacturer, String tagName, int c1, int c2, boolean syncOk, String statusDetails) {
			InventoryItemEntry entry = new InventoryItemEntry();
			entry.uid = uid;
			entry.marker = marker;
			entry.signature = signature;
			entry.manufacturer = manufacturer;
			entry.tagName = tagName;
			entry.index = c1;
			entry.statusOk = syncOk;
			entry.statusDetails = statusDetails;
			if (metadata != null && !metadata.isEmpty()) {
				entry.primaryItemId = metadata.getPrimaryItemId();
				entry.crcStatus = metadata.getCRCError() ? "FEHLER" : "OK";
			}
			items.add(entry);
		}

		@Override
		public void onReaderConnectionChanged(boolean connected, String deviceInfo) {
			this.connected = connected;
			this.deviceInfo = deviceInfo;
		}

		@Override
		public boolean isRunning() {
			return running;
		}

		@Override
		public boolean isDisposed() {
			return false;
		}

		@Override
		public void setCallback(InventoryCallback callback) {
			this.callback = callback;
		}

		@Override
		public void setThread(InventoryThread thread) {
			this.thread = thread;
		}
	}

	@Test
	@DisplayName("Test InventoryCallback with InventoryView decoupling")
	public void testCallbackWithMockView() throws Exception {
		MockInventoryView mockView = new MockInventoryView();
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("database.active", false);
		config.setProperty("webservice.active", false);

		InventoryCallback callback = new InventoryCallback(mockView, config);
		mockView.setCallback(callback);

		// Execute test scan
		callback.processTestScan("30119988");
		assertThat(mockView.items).hasSize(1);

		InventoryItemEntry item = mockView.items.get(0);
		assertThat(item.primaryItemId).isEqualTo("30119988");
		assertThat(item.marker).isEqualTo("Test-Shelf-A");
		assertThat(item.statusOk).isTrue();
		assertThat(item.crcStatus).isEqualTo("OK");
	}

	@Test
	@DisplayName("Test InventoryModernFrame creation and theme setup")
	public void testModernFrame() throws Exception {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("inventory.theme", "system");
		config.setProperty("inventory.marker", "OG-Test");
		config.setProperty("inventory.window.width", 1000);
		config.setProperty("inventory.window.height", 600);

		InventoryModernFrame.setupTheme(config);
		InventoryModernFrame frame = new InventoryModernFrame(config, null);

		assertThat(frame.getTagInfo()).isEqualTo("OG-Test");
		assertThat(frame.isRunning()).isFalse();

		// Test connection status callback
		frame.onReaderConnectionChanged(true, "FEIG ID ISC.MR102");
		frame.print("Test-Log", 1, 1);

		// Clean disposal
		frame.dispose();
	}

	@Test
	@DisplayName("Test backwards compatibility between InventoryItemEntry and InventoryDialog.InventoryItemEntry")
	public void testItemEntryInheritance() {
		InventoryDialog.InventoryItemEntry dialogEntry = new InventoryDialog.InventoryItemEntry();
		dialogEntry.primaryItemId = "ABC123";
		dialogEntry.statusOk = true;

		InventoryItemEntry baseEntry = dialogEntry;
		assertThat(baseEntry.primaryItemId).isEqualTo("ABC123");
		assertThat(baseEntry.statusOk).isTrue();
	}

	@Test
	@DisplayName("Test formatHexDump output with hex values and ASCII representation")
	public void testHexDumpFormatting() {
		byte[] testData = new byte[] {
			0x11, 0x01, 0x01, (byte) 'A',
			(byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E'
		};

		String dump = InventoryModernFrame.formatHexDump(testData);
		assertThat(dump).contains("Block 00:  11 01 01 41   |...A|");
		assertThat(dump).contains("Block 01:  42 43 44 45   |BCDE|");

		// Null or empty handling
		assertThat(InventoryModernFrame.formatHexDump(null)).isEmpty();
		assertThat(InventoryModernFrame.formatHexDump(new byte[0])).isEmpty();
	}
}

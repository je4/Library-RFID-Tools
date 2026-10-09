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
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
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

	@Test
	@DisplayName("Test that starting scan requires a location/marker in InventoryModernFrame")
	public void testScanRequiresMarkerInModernFrame() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("inventory.theme", "system");
		config.setProperty("inventory.marker", "");

		InventoryModernFrame.setupTheme(config);
		InventoryModernFrame frame = new InventoryModernFrame(config, null);

		try {
			assertThat(frame.getTagInfo()).isEmpty();
			assertThat(frame.isRunning()).isFalse();

			// Attempt to start scan without marker
			frame.toggleScanState();
			assertThat(frame.isRunning()).isFalse();

			// Enter a marker
			frame.tInventoryTag.setText("Regal-1-Fach-A");
			assertThat(frame.getTagInfo()).isEqualTo("Regal-1-Fach-A");

			// Start scan with marker present
			frame.toggleScanState();
			assertThat(frame.isRunning()).isTrue();

			// Clearing marker while running should pause scan
			frame.tInventoryTag.setText("");
			assertThat(frame.getTagInfo()).isEmpty();
			assertThat(frame.isRunning()).isFalse();
		} finally {
			frame.dispose();
		}
	}

	@Test
	@DisplayName("Test that InventoryModernFrame initializes cleanly in FlatLaf Dark and Light modes without styling exceptions")
	public void testModernFrameStylingCompatibility() {
		java.util.List<java.util.logging.LogRecord> capturedLogs = new java.util.ArrayList<>();
		java.util.logging.Handler logHandler = new java.util.logging.Handler() {
			@Override
			public void publish(java.util.logging.LogRecord record) {
				if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
					capturedLogs.add(record);
				}
			}
			@Override
			public void flush() {}
			@Override
			public void close() throws SecurityException {}
		};

		java.util.logging.Logger flatLafLogger = java.util.logging.Logger.getLogger("com.formdev.flatlaf");
		flatLafLogger.addHandler(logHandler);

		try {
			// Test dark theme styling initialization
			BaseConfiguration darkConfig = new BaseConfiguration();
			darkConfig.setProperty("inventory.theme", "dark");
			InventoryModernFrame.setupTheme(darkConfig);

			// Test components directly (works in both headless and GUI environments)
			javax.swing.JTable testTable = new javax.swing.JTable();
			testTable.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "intercellSpacing: 0,1; " +
					"[dark]background: #0f172a; [dark]foreground: #f8fafc; " +
					"[dark]selectionBackground: #2563eb; [dark]selectionForeground: #ffffff; " +
					"[dark]selectionInactiveBackground: #1d4ed8; [dark]selectionInactiveForeground: #ffffff; " +
					"[light]background: #ffffff; [light]foreground: #0f172a; " +
					"[light]selectionBackground: #3b82f6; [light]selectionForeground: #ffffff; " +
					"[light]selectionInactiveBackground: #60a5fa; [light]selectionInactiveForeground: #ffffff;");

			javax.swing.JTabbedPane testTabbedPane = new javax.swing.JTabbedPane();
			testTabbedPane.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "tabArc: 8; " +
					"[dark]selectedBackground: #1e293b; [dark]selectedForeground: #38bdf8; [dark]underlineColor: #38bdf8; " +
					"[light]selectedBackground: #ffffff; [light]selectedForeground: #0284c7; [light]underlineColor: #0284c7;");

			if (!java.awt.GraphicsEnvironment.isHeadless()) {
				InventoryModernFrame darkFrame = new InventoryModernFrame(darkConfig, null);
				darkFrame.dispose();
			}

			// Test light theme styling initialization
			BaseConfiguration lightConfig = new BaseConfiguration();
			lightConfig.setProperty("inventory.theme", "light");
			InventoryModernFrame.setupTheme(lightConfig);

			if (!java.awt.GraphicsEnvironment.isHeadless()) {
				InventoryModernFrame lightFrame = new InventoryModernFrame(lightConfig, null);
				lightFrame.dispose();
			}

			assertThat(capturedLogs)
					.withFailMessage("Expected no FlatLaf warning/severe logs but got: %s", capturedLogs)
					.isEmpty();
		} finally {
			flatLafLogger.removeHandler(logHandler);
		}
	}

	@Test
	@DisplayName("Test that InventoryThread enforces location/marker before unpausing")
	public void testInventoryThreadMarkerValidation() {
		MockInventoryView mockView = new MockInventoryView();
		mockView.marker = "";

		BaseConfiguration config = new BaseConfiguration();
		InventoryThread thread = new InventoryThread(null, null, mockView, config);

		assertThat(thread.isPaused()).isTrue();

		// Attempt to unpause without marker
		thread.pause(false);
		assertThat(thread.isPaused()).isTrue();

		// Set marker and unpause
		mockView.marker = "Standort-Regal-12";
		thread.pause(false);
		assertThat(thread.isPaused()).isFalse();

		// Re-pause
		thread.pause(true);
		assertThat(thread.isPaused()).isTrue();
	}

	@Test
	@DisplayName("Test that startup messages (config path, reader logs, DB and webservice status) are written to log")
	public void testStartupLogsOutputToLog() throws Exception {
		MockInventoryView mockView = new MockInventoryView();
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("config.file.path", "C:\\test\\inventory.xml");
		config.setProperty("database.active", false);
		config.setProperty("webservice.active", true);
		config.setProperty("webservice.url", "http://localhost:8080/api/inventory");
		config.setProperty("webservice.method", "POST");
		config.setProperty("webservice.jwt.key", "secret12345678901234567890");

		org.objectspace.rfid.library.ISO15693Reader mockReader = new org.objectspace.rfid.library.ISO15693Reader() {
			@Override
			public void inventory(org.objectspace.rfid.TagCallback callback, int numBlocks) {}
			@Override
			public void connect() {}
			@Override
			public void init() {}
			@Override
			public boolean isConnected() { return true; }
			@Override
			public boolean checkConnection() { return true; }
			@Override
			public String getDeviceInfo() { return "FEIG ISC.MR102-USB (12345678)"; }
			@Override
			public List<String> getStartupLogs() {
				return java.util.Arrays.asList(
					"Device found: 12345678",
					"Connecting to: 12345678",
					"FEIG Electronic ID ISC.MR102-USB HW:01.00 FW:02.00"
				);
			}
			@Override
			public void close() {}
		};

		new InventoryCallback(mockView, config, "C:\\test\\inventory.xml", mockReader, null);

		assertThat(mockView.logs).anyMatch(l -> l.contains("Konfigurationsdatei: C:\\test\\inventory.xml"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("Device found: 12345678"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("Connecting to: 12345678"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("FEIG Electronic ID ISC.MR102-USB HW:01.00 FW:02.00"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("[RFID-Reader] Verbunden: FEIG ISC.MR102-USB (12345678)"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("[Datenbank] Inaktiv"));
		assertThat(mockView.logs).anyMatch(l -> l.contains("[Webservice] Aktiv: POST http://localhost:8080/api/inventory"));
	}

	@Test
	@DisplayName("Test InventoryCallback reader and configuration getters")
	public void testInventoryCallbackReaderAccess() throws Exception {
		MockInventoryView mockView = new MockInventoryView();
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("config.file.path", "C:\\test\\inventory.xml");

		class TestReader implements org.objectspace.rfid.library.ISO15693Reader {
			@Override public void inventory(org.objectspace.rfid.TagCallback cb, int nb) {}
			@Override public void connect() {}
			@Override public void init() {}
			@Override public boolean isConnected() { return true; }
			@Override public boolean checkConnection() { return true; }
			@Override public String getDeviceInfo() { return "FEIG Test"; }
			@Override public boolean isMR102() { return true; }
			@Override public boolean hasError() { return true; }
			@Override public void close() {}
		}

		TestReader testReader = new TestReader();
		InventoryCallback cb = new InventoryCallback(mockView, config, "C:\\test\\inventory.xml", testReader);

		assertThat(cb.getReader()).isSameAs(testReader);
		assertThat(cb.getConfig()).isSameAs(config);
		assertThat(cb.getReader().isMR102()).isTrue();
		assertThat(cb.getReader().hasError()).isTrue();
	}

	@Test
	@DisplayName("Test InventoryModernFrame Host-Mode button presence and MR102 visibility")
	public void testHostModeButtonInModernFrame() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
		BaseConfiguration config = new BaseConfiguration();
		class MockMR102Reader implements org.objectspace.rfid.library.ISO15693Reader {
			@Override public void inventory(org.objectspace.rfid.TagCallback cb, int nb) {}
			@Override public void connect() {}
			@Override public void init() {}
			@Override public boolean isConnected() { return true; }
			@Override public boolean checkConnection() { return true; }
			@Override public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
			@Override public boolean isMR102() { return true; }
			@Override public void close() {}
		}

		MockMR102Reader reader = new MockMR102Reader();
		InventoryModernFrame frame = new InventoryModernFrame(config, reader);
		try {
			assertThat(frame.getBtnConfigureHostMode()).isNotNull();
			assertThat(frame.getBtnConfigureHostMode().isVisible()).isTrue();
		} finally {
			frame.dispose();
		}
	}
}

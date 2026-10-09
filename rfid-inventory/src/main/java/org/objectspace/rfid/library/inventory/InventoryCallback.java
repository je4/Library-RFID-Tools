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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.TreeSet;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;

import de.feig.fedm.utility.HexConvert;

/**
 * @author Juergen Enge
 *
 */
public class InventoryCallback implements TagCallback {

	protected static Connection conn = null;

	/**
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SQLException
	 * 
	 */
	public InventoryCallback(InventoryView dlg, AbstractConfiguration config)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this(dlg, config, config != null ? config.getString("config.file.path", null) : null, null, null);
	}

	public InventoryCallback(InventoryView dlg, AbstractConfiguration config, String configFilePath)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this(dlg, config, configFilePath, null, null);
	}

	public InventoryCallback(InventoryView dlg, AbstractConfiguration config, String configFilePath, ISO15693Reader reader)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this(dlg, config, configFilePath, reader, null);
	}

	public InventoryCallback(InventoryView dlg, AbstractConfiguration config, String configFilePath, ISO15693Reader reader, String readerStartupNotice)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this.config = config;
		this.dlg = dlg;
		this.reader = reader;
		if (configFilePath == null || configFilePath.trim().isEmpty()) {
			configFilePath = (config != null) ? config.getString("config.file.path", null) : null;
		}
		if (configFilePath == null || configFilePath.trim().isEmpty()) {
			configFilePath = new java.io.File("inventory.xml").getAbsolutePath();
		}
		this.configFilePath = configFilePath;

		// 1. Log configuration file
		println("Konfigurationsdatei: " + this.configFilePath, c1, c2);

		// 2. Log RFID Reader startup messages and connection status
		if (reader != null) {
			java.util.List<String> rdrLogs = reader.getStartupLogs();
			if (rdrLogs != null && !rdrLogs.isEmpty()) {
				for (String logMsg : rdrLogs) {
					println(logMsg, c1, c2);
				}
			}
			if (reader.isConnected()) {
				String devInfo = reader.getDeviceInfo();
				println("[RFID-Reader] Verbunden" + (devInfo != null ? ": " + devInfo : ""), c1, c2);
			} else {
				println("[RFID-Reader] Nicht verbunden", c1, c2);
			}
		}
		if (readerStartupNotice != null && !readerStartupNotice.trim().isEmpty()) {
			println("[RFID-Reader] " + readerStartupNotice.trim(), c1, c2);
		}

		sessionName = LocalDateTime.now().toString();

		// 3. Database connection & logging
		String dbError = null;
		if (conn == null) {
			if (config != null) {
				if (config.getBoolean("database.active", false)) {
					String driver = config.getString("database.driver");
					String dsn = config.getString("database.dsn");

					if (driver != null && dsn != null) {
						try {
							Class.forName(driver).newInstance();
							conn = DriverManager.getConnection(dsn);
							conn.setAutoCommit(true);
							println("[Datenbank] Verbunden mit: " + dsn, c1, c2);
						} catch (Exception e) {
							dbError = e.getMessage();
							System.err.println("Database connection failed: " + dbError);
							println("[Datenbank Fehler] Verbindung fehlgeschlagen: " + dbError, c1, c2);
						}
					}
				} else {
					println("[Datenbank] Inaktiv", c1, c2);
				}
			}
		} else {
			println("[Datenbank] Bereits verbunden", c1, c2);
		}

		uidList = new TreeSet<String>();

		// 4. Webservice configuration & logging
		webserviceConfig = WebserviceConfig.fromConfiguration(config);
		if (webserviceConfig != null && webserviceConfig.isActive()) {
			webserviceDispatcher = new WebserviceDispatcher(webserviceConfig);
			int keyLen = (webserviceConfig.getJwtKey() != null) ? webserviceConfig.getJwtKey().length() : 0;
			println("[Webservice] Aktiv: " + webserviceConfig.getHttpMethod() + " " + webserviceConfig.getTargetUrl()
					+ " (JWT-Key-L\u00E4nge: " + keyLen + (keyLen > 0 ? " Zeichen" : " (kein Key konfiguriert)") + ")", c1, c2);
		} else {
			println("[Webservice] Inaktiv", c1, c2);
		}

		if (conn != null) {
			String insertSQL = "REPLACE INTO `rfid`.`inventory` "
					+ "(`uid`, `version`, `usagetype`, `parts`, `partno`, `itemid`, `country`, `isil`, `inventorytime`"
					+ ", `marker`, `sessionname`, `raw`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?)";
			stmt = conn.prepareStatement(insertSQL);

			String selectsql = "SELECT signatur FROM code_sig WHERE barcode=?";
			stmt2 = conn.prepareStatement(selectsql);
		}
	}

	protected void print(String txt, int c1, int c2) {
		if (dlg != null && !dlg.isDisposed()) {
			dlg.print(txt, c1, c2);
		}
	}

	private String tagInfo;

	protected String getTagInfo() {
		if (dlg != null && !dlg.isDisposed()) {
			return dlg.getTagInfo();
		}
		return "";
	}

	protected void println(String txt, int c1, int c2) {
		print(txt + "\n", c1, c2);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.TagCallback#doIt(int, int, java.lang.String,
	 * java.lang.String, java.lang.String, byte[], long)
	 */
	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long blockSize) throws Exception {
		byte[] block = null;

		c2++;
		if (uidList.contains(UID)) {
			// println("UID already in inventory: " + UID);
			print("", c1, c2 );
			return null;
		}

		c1++;
		FinnishDataModel metadata = new FinnishDataModel();
		String txt = "Manufacturer Name: " + manufacturerName + "\n";
		txt += "Tag Name: " + tagName + "\n";
		txt += "UID: " + UID + "\n";
		print(txt, c1, c2);

		try {
			metadata.setBlock(data, blockSize);

			txt = "";
			String sig = "";
			if (metadata.isEmpty()) {
				txt += "empty" + "\n";
			} else {
				String tagInfo = getTagInfo();
				txt += "Type of usage: " + metadata.getTypeOfUsage() + "\n";
				txt += "Parts in item: " + metadata.getPartsInItem() + "\n";
				txt += "Part number: " + metadata.getPartNumber() + "\n";
				txt += "Primary item ID: " + metadata.getPrimaryItemId() + "\n";
				txt += "CRC (lsb): " + HexConvert.toHexString(metadata.getCRCBytes())
						+ (metadata.getCRCError() ? " Error" : " OK") + "\n";
				txt += "Country of owner library: " + metadata.getCountryOfOwnerLib() + "\n";
				txt += "ISIL: " + metadata.getISIL() + "\n";
				txt += "Marker: " + tagInfo + "(" + c1 + "/" + c2 + ")\n";

				if (stmt2 != null) {
					stmt2.setString(1, metadata.getPrimaryItemId());
					try {
						ResultSet rs = stmt2.executeQuery();
						while (rs.next()) {
							sig += rs.getString(1) + "    ";
						}
						rs.close();

					} catch (SQLException e) {
						e.printStackTrace();
						println("Error: " + e.getMessage(), c1, c2);
					}
				}
				if (sig == "")
					sig = "not found!!!";
				txt += "Signature: " + sig.trim() + "\n";
			}
			println(txt, c1, c2);

			String currentMarker = getTagInfo();
			String finalSig = sig.trim();

			boolean isDbConfigured = (config != null && config.getBoolean("database.active", false));
			boolean dbSuccess = true;
			String dbError = null;

			if (isDbConfigured) {
				if (stmt != null) {
					stmt.setString(1, UID);
					stmt.setInt(2, metadata.getVersion());
					stmt.setInt(3, metadata.getTypeOfUsage());
					stmt.setInt(4, metadata.getPartsInItem());
					stmt.setInt(5, metadata.getPartNumber());
					stmt.setString(6, metadata.getPrimaryItemId());
					stmt.setString(7, metadata.getCountryOfOwnerLib());
					stmt.setString(8, metadata.getISIL());
					stmt.setString(9, tagInfo);
					stmt.setString(10, sessionName);
					stmt.setBytes(11, metadata.getData());
					try {
						int numRows = stmt.executeUpdate();
						dbSuccess = true;
					} catch (SQLException e) {
						e.printStackTrace();
						println("Error: " + e.getMessage(), c1, c2);
						dbSuccess = false;
						dbError = e.getMessage();
					}
				} else {
					dbSuccess = false;
					dbError = "Keine aktive Datenbankverbindung";
				}
			}

			boolean isWsConfigured = (webserviceConfig != null && webserviceConfig.isActive());
			boolean wsSuccess = true;
			String wsError = null;

			if (isWsConfigured) {
				if (webserviceDispatcher != null) {
					try {
						String rawHex = (metadata.getData() != null) ? HexConvert.toHexString(metadata.getData()) : "";
						WebserviceResponse wsResp = webserviceDispatcher.dispatchScan(
								currentMarker,
								currentMarker,
								metadata.isEmpty() ? "empty" : metadata.getPrimaryItemId(),
								rawHex,
								UID,
								metadata,
								sessionName,
								null
						);
						if (wsResp != null && wsResp.getHttpStatus() == 200) {
							wsSuccess = true;
							if (webserviceConfig != null && webserviceConfig.isDebugMode()) {
								println("[Webservice] " + wsResp.getHttpStatus() + " (" + wsResp.getDurationMs() + "ms): " + wsResp.getResponseBody(), c1, c2);
							}
						} else {
							wsSuccess = false;
							if (wsResp != null) {
								wsError = "HTTP " + wsResp.getHttpStatus() + (wsResp.getErrorMessage() != null ? " (" + wsResp.getErrorMessage() + ")" : "");
								println(wsResp.formatScanLogDetails(), c1, c2);
							} else {
								wsError = "Keine Antwort erhalten";
								println("[Webservice Fehler] Keine Antwort erhalten", c1, c2);
							}
						}
					} catch (Exception e) {
						wsSuccess = false;
						wsError = e.getMessage();
						int keyLen = (webserviceConfig != null && webserviceConfig.getJwtKey() != null) ? webserviceConfig.getJwtKey().length() : 0;
						println("[Webservice Fehler] " + e.getMessage() + "\n  JWT-Key-L\u00E4nge: " + keyLen + (keyLen > 0 ? " Zeichen" : " (kein Key konfiguriert)"), c1, c2);
					}
				} else {
					wsSuccess = false;
					wsError = "Webservice-Dispatcher nicht initialisiert";
				}
			}

			boolean allConfiguredSuccess = (!isDbConfigured || dbSuccess) && (!isWsConfigured || wsSuccess);
			StringBuilder detailsSb = new StringBuilder();
			if (!isDbConfigured && !isWsConfigured) {
				detailsSb.append("Lokal (keine Remote-Dienste aktiv)");
			} else {
				if (isDbConfigured) {
					detailsSb.append("DB: ").append(dbSuccess ? "OK" : ("Fehler (" + dbError + ")"));
				}
				if (isWsConfigured) {
					if (detailsSb.length() > 0) detailsSb.append(", ");
					detailsSb.append("Webservice: ").append(wsSuccess ? "OK (HTTP 200)" : ("Fehler (" + wsError + ")"));
				}
			}
			String statusDetails = detailsSb.toString();

			if (dlg != null && !dlg.isDisposed()) {
				dlg.addInventoryItem(UID, metadata, finalSig, currentMarker, manufacturerName, tagName, c1, c2, allConfiguredSuccess, statusDetails);
			}

			uidList.add(UID);

		} catch (Exception ex) {
			// empty tag
			/*
			 * if (metadata.getVersion() == 0) { metadata.setValues(1, 1, 1,
			 * "testing", "DE", "HIL3/9"); block = metadata.getBlock(); }
			 */
		}

		return block;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.TagCallback#empty()
	 */
	@Override
	public void empty() {
		// TODO Auto-generated method stub

	}

	/**
	 * clear the list of known uid's
	 */
	public void clearUIDList() {
		uidList.clear();
		c1 = 0;
		c2 = 0;
		print("", c1, c2);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectspace.rfid.TagCallback#close()
	 */
	@Override
	public void close() throws Exception {
		if (stmt != null) {
			try {
				stmt.close();
			} catch (Exception e) {
			}
		}
		if (stmt2 != null) {
			try {
				stmt2.close();
			} catch (Exception e) {
			}
		}
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
			}
		}
	}

	public WebserviceConfig getWebserviceConfig() {
		return webserviceConfig;
	}

	public WebserviceDispatcher getWebserviceDispatcher() {
		return webserviceDispatcher;
	}

	public void setWebserviceDispatcher(WebserviceDispatcher webserviceDispatcher) {
		this.webserviceDispatcher = webserviceDispatcher;
	}

	public String getConfigFilePath() {
		return configFilePath;
	}

	public ISO15693Reader getReader() {
		return reader;
	}

	public AbstractConfiguration getConfig() {
		return config;
	}

	/**
	 * Triggers an asynchronous mock/test RFID tag scan analogous to the Android NFC Reader application (triggerTestScan).
	 * Generates a mock FinnishDataModel tag (Version 1, UsageType 1, Parts 1, PartNo 1, ItemId 3011xxxx, CH, ISIL-123)
	 * and dispatches it through the normal scan processing chain (Webservice, Database, UI metrics, Log, etc.).
	 *
	 * @return The generated mock UID
	 */
	public String triggerTestScan() {
		return triggerTestScan(null);
	}

	/**
	 * Triggers an asynchronous mock/test RFID tag scan with an optional custom barcode/itemId.
	 *
	 * @param customItemId Optional custom item ID / barcode (if null or blank, "3011" + 4-digit random is used)
	 * @return The generated mock UID
	 */
	public String triggerTestScan(String customItemId) {
		int randomSuffix = 1000 + (int) (Math.random() * 9000);
		String mockUid = String.format("E0040150%04dABCD", randomSuffix);
		String itemId = (customItemId != null && !customItemId.trim().isEmpty())
				? customItemId.trim()
				: ("3011" + randomSuffix);

		new Thread(() -> {
			try {
				processTestScanInternal(mockUid, itemId);
			} catch (Exception e) {
				e.printStackTrace();
				println("[Test-Scan Fehler] " + e.getMessage(), c1, c2);
			}
		}, "TestScan-Thread").start();

		return mockUid;
	}

	/**
	 * Processes a mock/test RFID tag scan synchronously.
	 *
	 * @param customItemId Optional custom item ID / barcode
	 * @return The processed block data
	 * @throws Exception on error
	 */
	public byte[] processTestScan(String customItemId) throws Exception {
		int randomSuffix = 1000 + (int) (Math.random() * 9000);
		String mockUid = String.format("E0040150%04dABCD", randomSuffix);
		String itemId = (customItemId != null && !customItemId.trim().isEmpty())
				? customItemId.trim()
				: ("3011" + randomSuffix);

		return processTestScanInternal(mockUid, itemId);
	}

	private byte[] processTestScanInternal(String mockUid, String itemId) throws Exception {
		FinnishDataModel model = new FinnishDataModel();
		model.setValues(1, 1, 1, itemId, "CH", "ISIL-123", null);
		byte[] data = model.getBlock(48);

		return doIt(c1, c2, "NXP Semiconductors (Test)", "ISO 15693 : NXP I-Code SLIX (Test)", mockUid, data, 4);
	}

	protected AbstractConfiguration config;
	protected ISO15693Reader reader = null;
	protected String configFilePath = null;
	protected WebserviceConfig webserviceConfig = null;
	protected WebserviceDispatcher webserviceDispatcher = null;
	protected String sessionName;
	protected TreeSet<String> uidList = null;
	protected PreparedStatement stmt = null;
	protected PreparedStatement stmt2 = null;
	protected String marker = null;
	protected InventoryView dlg = null;
	protected int c1 = 0;
	protected int c2 = 0;

}

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

import de.feig.FeHexConvert;

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
	public InventoryCallback(InventoryDialog dlg, AbstractConfiguration config)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this.config = config;
		this.dlg = dlg;
		sessionName = LocalDateTime.now().toString();

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
						} catch (Exception e) {
							System.err.println("Database connection failed: " + e.getMessage());
						}
					}
				}
			}
		}

		uidList = new TreeSet<String>();

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
		if (!dlg.isDisposed())
			dlg.getDisplay().syncExec(new Runnable() {
				public void run() {
					dlg.print(txt, c1, c2);
				}
			});
	}

	private String tagInfo;

	protected String getTagInfo() {
		if (!dlg.isDisposed())
			dlg.getDisplay().syncExec(new Runnable() {
				public void run() {
					tagInfo = dlg.tInventoryTag.getText().trim();
				}
			});
		return tagInfo;
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
				txt += "CRC (lsb): " + FeHexConvert.byteArrayToHexString(metadata.getCRCBytes())
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
			if (!dlg.isDisposed()) {
				dlg.addInventoryItem(UID, metadata, finalSig, currentMarker, manufacturerName, tagName, c1, c2);
			}

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
					uidList.add(UID);
				} catch (SQLException e) {
					e.printStackTrace();
					println("Error: " + e.getMessage(), c1, c2);
				}
			} else {
				uidList.add(UID);
			}

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

	protected AbstractConfiguration config;
	protected String sessionName;
	protected TreeSet<String> uidList = null;
	protected PreparedStatement stmt = null;
	protected PreparedStatement stmt2 = null;
	protected String marker = null;
	protected InventoryDialog dlg = null;
	protected int c1 = 0;
	protected int c2 = 0;

}

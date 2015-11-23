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
package org.objectspace.rfid.library.inventory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.TreeSet;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.TagCallback;

import de.feig.FeHexConvert;

/**
 * @author Juergen Enge
 *
 */
public class DoInventory implements TagCallback {

	/**
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SQLException
	 * 
	 */
	public DoInventory(AbstractConfiguration config)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this.config = config;
		sessionName = LocalDateTime.now().toString();

		if (conn == null) {

			if (config != null) {
				String driver = config.getString("database.driver");
				String dsn = config.getString("database.dsn");

				if (driver != null && dsn != null) {
					Class.forName(driver).newInstance();
					conn = DriverManager.getConnection(dsn);
					conn.setAutoCommit(true);
				}
			}
		}

		uidList = new TreeSet<String>();

		String insertSQL = "INSERT INTO `rfid`.`inventory` "
				+ "(`uid`, `version`, `usagetype`, `parts`, `partno`, `itemid`, `country`, `isil`, `inventorytime`"
				+ ", `marker`, `sessionname`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?)";
		stmt = conn.prepareStatement(insertSQL);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info_age.rfid.TagCallback#doIt(java.lang.String, java.lang.String,
	 * java.lang.String, byte[], long)
	 */
	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data, long blockSize)
			throws Exception {
		byte[] block = null;

		if (uidList.contains(UID)) {
			System.out.println("UID already in inventory: " + UID);
			return null;
		}

		FinnishDataModel metadata = new FinnishDataModel();
		System.out.println("Manufacturer Name: " + manufacturerName);
		System.out.println("Tag Name: " + tagName);
		System.out.println("UID: " + UID);

		try {
			metadata.setBlock(data, blockSize);

			System.out.println("Type of usage: " + metadata.getTypeOfUsage());
			System.out.println("Parts in item: " + metadata.getPartsInItem());
			System.out.println("Part number: " + metadata.getPartNumber());
			System.out.println("Primary item ID: " + metadata.getPrimaryItemId());
			System.out.println("CRC (lsb): " + FeHexConvert.byteArrayToHexString(metadata.getCRCBytes()));
			System.out.println("Country of owner library: " + metadata.getCountryOfOwnerLib());
			System.out.println("ISIL: " + metadata.getISIL());

			stmt.setString(1, UID);
			stmt.setInt(2, metadata.getVersion());
			stmt.setInt(3, metadata.getTypeOfUsage());
			stmt.setInt(4, metadata.getPartsInItem());
			stmt.setInt(5, metadata.getPartNumber());
			stmt.setString(6, metadata.getPrimaryItemId());
			stmt.setString(7, metadata.getCountryOfOwnerLib());
			stmt.setString(8, metadata.getISIL());
			stmt.setString(9, marker);
			stmt.setString(10, sessionName);
			try {
				int numRows = stmt.executeUpdate();
				uidList.add(UID);
			} catch (SQLException e) {
				e.printStackTrace();
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

	public void close() throws SQLException {
		stmt.close();
		conn.close();
	}

	protected AbstractConfiguration config;
	protected String sessionName;
	protected TreeSet<String> uidList = null;
	protected PreparedStatement stmt = null;
	protected String marker = null;

	protected static Connection conn = null;

	@Override
	public void empty() {
		// TODO Auto-generated method stub
		
	}
}

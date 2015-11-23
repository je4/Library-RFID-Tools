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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.wb.swt.SWTResourceManager;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.FinnishDataModelRegex;
import org.objectspace.rfid.TagCallback;

import de.feig.FeHexConvert;

/**
 * @author Juergen Enge
 *
 */
public class TagHandleInventory implements TagCallback {

	/**
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SQLException
	 * 
	 */
	public TagHandleInventory(AbstractConfiguration config, MainDialog mainDialog, WebCamThread wct)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		this.config = config;
		this.mainDialog = mainDialog;
		this.wct = wct;
		countryOfOwnerLib = config.getString("taghandle.countryofownerlib");
		ISIL = config.getString("taghandle.isil");
		usage = config.getInt("taghandle.usage");
		imageFile = config.getString("taghandle.imagefile", null);

		if (conn == null) {

			if (config != null) {
				if (config.getBoolean("database.active", false)) {
					String driver = config.getString("database.driver");
					String dsn = config.getString("database.dsn");

					if (driver != null && dsn != null) {
						Class.forName(driver).newInstance();
						conn = DriverManager.getConnection(dsn);
						conn.setAutoCommit(true);
					}
				}
			}
		}

		regex = new HashMap<String, FinnishDataModelRegex>();
		for (String fld : FinnishDataModel.stringFieldNames) {
			FinnishDataModelRegex r = new FinnishDataModelRegex(config, fld);
			regex.put(fld, r);
		}

		uidList = new TreeSet<String>();
		if (conn != null) {
			String insertSQL = "INSERT INTO `rfid`.`inventory` "
					+ "(`uid`, `version`, `usagetype`, `parts`, `partno`, `itemid`, `country`, `isil`, `inventorytime`"
					+ ", `marker`, `sessionname`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?)";

			stmt = conn.prepareStatement(insertSQL);
		}

	}

	public void writeMainDialog(int typeOfUsage, int partsInItem, int partNumber, String primaryItemId,
			String countryOfOwnerLib, String ISIL, byte[] crc, boolean crcError, boolean isEmpty, Image img,
			TreeSet<String> uidList, String UID, String tagName, String manufacturerName, byte[] data) {
		if (!mainDialog.isDisposed())
			mainDialog.getDisplay().syncExec(new Runnable() {
				public void run() {
					mainDialog.txtCode.setText(primaryItemId);
					mainDialog.txtLibraryCountry.setText(countryOfOwnerLib);
					mainDialog.txtISIL.setText(ISIL);
					mainDialog.cbUsage.select(ArrayUtils.indexOf(usageList, typeOfUsage));
					mainDialog.parts.setText(((Integer) partsInItem).toString());
					mainDialog.part.setText(((Integer) partNumber).toString());
					mainDialog.txtCode.setFocus();
					mainDialog.txtCode.selectAll();
					mainDialog.txtUID.setText(UID);
					if (mainDialog.bookImage != null)
						mainDialog.bookImage.dispose();
					mainDialog.bookImage = img;
					mainDialog.bookCanvas.redraw();
					mainDialog.lstUIDs.removeAll();
					if (uidList != null) {
						for (String uid : uidList) {
							mainDialog.lstUIDs.add(uid);
						}
					}
					mainDialog.txtManufacturerName.setText(manufacturerName);
					mainDialog.txtTagName.setText(tagName);
					if (crcError)
						mainDialog.txtCRC.setForeground(SWTResourceManager.getColor(SWT.COLOR_RED));
					else
						mainDialog.txtCRC.setForeground(SWTResourceManager.getColor(SWT.COLOR_DARK_GREEN));
					mainDialog.txtCRC.setText(String.format("%02x %02x", crc[0], crc[1]));
					mainDialog.btnEmpty.setSelection(isEmpty);
					mainDialog.setData(data);

				}
			});

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info_age.rfid.TagCallback#empty()
	 */
	@Override
	public void empty() {
		uidListOld = new TreeSet<String>();
		uidList = new TreeSet<String>();
		writeMainDialog(-1, 0, 0, "", "", "", new byte[] { 0x00, 0x00 }, false, false, null, null, "", "", "", null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info_age.rfid.TagCallback#doIt(java.lang.String, java.lang.String,
	 * java.lang.String, byte[], long)
	 */
	@Override
	public byte[] doIt(int counter, int elements, String manufacturerName, String tagName, String UID, byte[] data,
			long numBlocks) throws Exception {
		if (counter == 0) {
			uidListOld = uidList;
			uidList = new TreeSet<String>();
		}
		block = null;

		if (!uidList.contains(UID)) {
			uidList.add(UID);
		}
		// System.out.println( "UID: " + UID + " (" + counter + "/" + elements +
		// "[" + lastCount + "]) - " + uidList.size());

		if (elements > 1 && counter >= elements - 1) {
			if (!uidListOld.equals(uidList) || lastCount != elements) {
				writeMainDialog(-1, 0, 0, "", "", "", new byte[] { 0x00, 0x00 }, false, true, null, uidList, "", "", "",
						data);
			}
		} else if (elements > 1 && counter < elements - 1) {
			// there should be another
		} else if (elements == 1) {
			if (!uidListOld.equals(uidList) || lastCount != elements) {

				FinnishDataModel metadata = new FinnishDataModel();
				System.out.println("Manufacturer Name: " + manufacturerName);
				System.out.println("Tag Name: " + tagName);
				System.out.println("UID: " + UID);

				writeMainDialog(-1, 0, 0, "", "", "", new byte[] { 0x00, 0x00 }, false, true, null, null, UID, tagName,
						manufacturerName, data);
				try {
					metadata.setBlock(data, numBlocks);

					if (metadata.isEmpty()) {
						// empty tag
						writeMainDialog(usage, 1, 1, "", countryOfOwnerLib, ISIL, new byte[] { 0x00, 0x00 }, false,
								metadata.isEmpty(), null, null, UID, tagName, manufacturerName, data);
					} else {
						if (metadata.getCRCError()) {
							System.out.println("CRC ERROR!!!!");
						}

						System.out.println("Type of usage: " + metadata.getTypeOfUsage());
						System.out.println("Parts in item: " + metadata.getPartsInItem());
						System.out.println("Part number: " + metadata.getPartNumber());
						System.out.println("Primary item ID: " + metadata.getPrimaryItemId());
						System.out.println("CRC (lsb): " + FeHexConvert.byteArrayToHexString(metadata.getCRCBytes()));
						System.out.println("Country of owner library: " + metadata.getCountryOfOwnerLib());
						System.out.println("ISIL: " + metadata.getISIL());
						String imgName = String.format(imageFile, metadata.getPrimaryItemId());
						Image img = null;
						if (new File(imgName).isFile()) {
							img = new Image(mainDialog.getDisplay(), imgName);
						}

						writeMainDialog(metadata.getTypeOfUsage(), metadata.getPartsInItem(), metadata.getPartNumber(),
								metadata.getPrimaryItemId(), metadata.getCountryOfOwnerLib(), metadata.getISIL(),
								metadata.getCRCOrigBytes(), metadata.getCRCError(), metadata.isEmpty(), img, null, UID,
								tagName, manufacturerName, data);
						if (metadata.doRegex(regex)) {
							System.out.println("Autocorrect!!!");
							block = metadata.getBlock(data.length);
							uidList = new TreeSet<String>();
						}
					}
					/*
					 * if (metadata.getPrimaryItemId().equals("HIL3$00426288"))
					 * { block = data; Arrays.fill(block, (byte) 0); }
					 */
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				if (!mainDialog.isDisposed())
					mainDialog.getDisplay().syncExec(new Runnable() {
						public void run() {

							if (UID != null && UID.equals(mainDialog.store)) {
								FinnishDataModel metadata = new FinnishDataModel();
								int sel = mainDialog.cbUsage.getSelectionIndex();
								int typeOfUsage = usageList[sel];
								int partsInItem = Integer.parseInt(mainDialog.parts.getText());
								int partNumber = Integer.parseInt(mainDialog.part.getText());
								String primaryItemId = mainDialog.txtCode.getText();
								String countryOfOwnerLib = mainDialog.txtLibraryCountry.getText();
								String ISIL = mainDialog.txtISIL.getText();
								metadata.setValues(typeOfUsage, partsInItem, partNumber, primaryItemId,
										countryOfOwnerLib, ISIL);
								block = metadata.getBlock(data.length);
								if (imageFile != null && wct != null) {
									try {
										wct.storeImage(String.format(imageFile, primaryItemId));
									} catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								uidList = new TreeSet<String>();
							} else if (UID != null && UID.equals(mainDialog.delete)) {
								block = new byte[data.length];
								Arrays.fill(block, (byte) 0x00);
								uidList = new TreeSet<String>();
							}
							mainDialog.store = null;
							mainDialog.delete = null;
						}
					});
			}
		}
		lastCount = elements;
		return block;
	}

	public void close() throws SQLException {
		if (conn != null) {
			stmt.close();
			conn.close();
		}
	}

	protected AbstractConfiguration config;
	protected TreeSet<String> uidList = null;
	protected TreeSet<String> uidListOld = null;
	protected PreparedStatement stmt = null;
	protected MainDialog mainDialog = null;
	protected String countryOfOwnerLib = null;
	protected String ISIL = null;
	protected int usage = 0;
	protected byte[] block = null;
	protected int lastCount = 0;
	protected WebCamThread wct = null;
	protected String imageFile;
	protected HashMap<String, FinnishDataModelRegex> regex;

	protected static Connection conn = null;
	protected static int[] usageList = { -1, 0, 1, 2, 7, 8 };

}

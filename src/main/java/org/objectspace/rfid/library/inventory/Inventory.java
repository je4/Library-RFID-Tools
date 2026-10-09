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

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.objectspace.rfid.ISO15693ReaderFactory;
import org.objectspace.rfid.library.ISO15693Reader;

/**
 * Application for creating a library inventory. writes rfid tag data into sql database.
 * @author Juergen Enge
 *
 */
public class Inventory {

	/**
	 * default constructor
	 */
	public Inventory() {
	}

	/**
	 * main function
	 * needs configuration
	 * * inventory.window.logo (name of image file with logo)
	 * * inventory.window.background (optional, name of image file with background)
	 * * inventory.window.posx/inventory.window.posy (position of window)
	 * * inventory.window.width/inventory.windows.height (size of window)
	 * @param args args[0] is config file name
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String configfilename = "inventory.xml";
		if (args.length > 0) {
			configfilename = args[0];
		}

		java.io.File configFile = new java.io.File(configfilename);
		String configAbsolutePath = configFile.getAbsolutePath();

		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(
				XMLConfiguration.class).configure(params.xml().setFile(configFile));

		XMLConfiguration config = builder.getConfiguration();
		config.setProperty("config.file.path", configAbsolutePath);

		ISO15693Reader reader = null;
		String readerStartupNotice = null;
		try {
			reader = ISO15693ReaderFactory.createReader(config);
			reader.connect();
			reader.init();
		} catch (Exception e) {
			readerStartupNotice = "Notice: RFID reader not connected (" + e.getMessage() + "). Application starting anyway.";
			System.out.println(readerStartupNotice);
		}
		
		String ui = config.getString("inventory.ui", "modern").trim().toLowerCase();
		if ("swt".equals(ui)) {
			launchSwt(config, configAbsolutePath, reader, readerStartupNotice);
		} else {
			launchModern(config, configAbsolutePath, reader, readerStartupNotice);
		}
	}

	private static void launchModern(XMLConfiguration config, String configAbsolutePath, ISO15693Reader reader, String readerStartupNotice) {
		InventoryModernFrame.setupTheme(config);
		final ISO15693Reader finalReader = reader;
		final String finalNotice = readerStartupNotice;
		javax.swing.SwingUtilities.invokeLater(() -> {
			try {
				InventoryModernFrame frame = new InventoryModernFrame(config, finalReader);
				InventoryCallback callback = new InventoryCallback(frame, config, configAbsolutePath, finalReader, finalNotice);
				frame.setCallback(callback);
				InventoryThread inventoryThread = new InventoryThread(finalReader, callback, frame, config);
				frame.setThread(inventoryThread);
				Thread runner = new Thread(inventoryThread, "Inventory-Scanner");
				runner.start();
				frame.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private static void launchSwt(XMLConfiguration config, String configAbsolutePath, ISO15693Reader reader, String readerStartupNotice) throws Exception {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setText("RFID Inventory - info-age GmbH, Basel");

		// Set application window and taskbar icon
		try {
			java.io.File iconPng = new java.io.File("app.png");
			java.io.File iconIco = new java.io.File("app.ico");
			if (iconPng.exists()) {
				shell.setImage(new Image(display, iconPng.getAbsolutePath()));
			} else if (iconIco.exists()) {
				shell.setImage(new Image(display, iconIco.getAbsolutePath()));
			}
		} catch (Exception e) {
			// Ignore icon loading errors
		}

		FillLayout layout = new FillLayout();
		shell.setLayout(layout);

		String logoName = config.getString("inventory.window.logo");
		Image logo = null;
		if (logoName != null && new java.io.File(logoName).exists()) {
			logo = new Image(display, logoName);
		}
		String bgImgName = config.getString("inventory.window.background");
		Image background = null;
		if (bgImgName != null && new java.io.File(bgImgName).exists()) {
			background = new Image(display, bgImgName);
		}

		InventoryDialog md = new InventoryDialog(shell, SWT.NONE, logo, background);
		shell.setLocation(config.getInt("inventory.window.posx", 100), config.getInt("inventory.window.posy", 100));
		shell.setSize(config.getInt("inventory.window.width", 1150), config.getInt("inventory.window.height", 700));
		shell.open();
		
		InventoryCallback callback = new InventoryCallback(md, config, configAbsolutePath, reader, readerStartupNotice);
		md.setCallback(callback);
		InventoryThread inventoryThread = new InventoryThread(reader, callback, md, config);
		md.setThread(inventoryThread);
		Thread runner = new Thread(inventoryThread);
		runner.start();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		inventoryThread.dispose();
		
		if (reader != null && reader.isConnected()) {
			try {
				reader.close();
			} catch (Exception e) {
				// Ignore
			}
		}

	}

}

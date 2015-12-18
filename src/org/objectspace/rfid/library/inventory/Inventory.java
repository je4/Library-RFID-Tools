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
import org.objectspace.rfid.library.taghandle.MainDialog;

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
		String configfilename = "tagreader.xml";
		if (args.length > 0) {
			configfilename = args[0];
		}

		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(
				XMLConfiguration.class).configure(params.xml().setFileName(configfilename));

		XMLConfiguration config = builder.getConfiguration();

		ISO15693Reader reader = ISO15693ReaderFactory.createReader(config);
		reader.connect();
		reader.init();
		
		Display display = new Display();
		Shell shell = new Shell(display);

		FillLayout layout = new FillLayout();
		shell.setLayout(layout);

		Image logo = new Image(display, config.getString("inventory.window.logo"));
		String bgImgName = config.getString("inventory.window.background");
		Image background = null;
		if( bgImgName != null ) background = new Image(display, bgImgName );

		InventoryDialog md = new InventoryDialog(shell, SWT.NONE, logo, background);
		shell.setLocation(config.getInt("inventory.window.posx", 100), config.getInt("inventory.window.posy", 100));
		shell.setSize(config.getInt("inventory.window.width", 1150), config.getInt("inventory.window.height", 700));
		shell.open();
		
		InventoryCallback callback = new InventoryCallback( md, config);
		InventoryThread inventoryThread = new InventoryThread(reader, callback, md, config);
		md.setThread(inventoryThread);
		Thread runner = new Thread(inventoryThread);
		runner.start();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		inventoryThread.dispose();
		
		reader.close();

	}

}

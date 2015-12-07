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

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.objectspace.rfid.ISO15693ReaderFactory;
import org.objectspace.rfid.library.ISO15693Reader;
import de.feig.FedmException;

/**
 * @author Juergen Enge
 *
 */
public class TagHandle {

	/**
	 * 
	 */
	public TagHandle() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws Exception
	 * @throws FedmException
	 */
	public static void main(String[] args) throws FedmException, Exception {
		String configfilename = "tagreader.xml";
		if (args.length > 0) {
			configfilename = args[0];
		}

		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(
				XMLConfiguration.class).configure(params.xml().setFileName(configfilename));

		XMLConfiguration config = builder.getConfiguration();

		ISO15693Reader reader = ISO15693ReaderFactory.createReader(config);
		
		//FeigRFID feig = new FeigRFID(config);
		reader.connect();
		reader.init();

		Display display = new Display();
		Shell shell = new Shell(display);

		FillLayout layout = new FillLayout();
		shell.setLayout(layout);

		Image logo = new Image(display, config.getString("taghandle.window.logo"));
		String bgImgName = config.getString("taghandle.window.background");
		Image background = null;
		if( bgImgName != null ) background = new Image(display, bgImgName );

		MainDialog md = new MainDialog(shell, SWT.NONE, logo, background, config.getInt("taghandle.camera.edgetresholdmin", 100), config.getInt("taghandle.camera.edgetresholdmax", 200));
		shell.setLocation(config.getInt("taghandle.window.posx", 100), config.getInt("taghandle.window.posy", 100));
		shell.setSize(config.getInt("taghandle.window.width", 1150), config.getInt("taghandle.window.height", 650));
		shell.open();

		WebCamThread webCamThread = null;
		if (config.getBoolean("taghandle.camera.active")) {
			webCamThread = new WebCamThread(config, md);
			Thread runner = new Thread(webCamThread);
			runner.start();
		}

		TagHandleThread tagReaderThread = new TagHandleThread(reader, config, md, webCamThread);
		Thread runner2 = new Thread(tagReaderThread);
		runner2.start();

		// CanvasFrame cFrame = new CanvasFrame("Capture Preview");
		// cFrame.setBounds(744, 179, 348, 227);

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		tagReaderThread.dispose();
		if (webCamThread != null)
			webCamThread.dispose();
		if (!display.isDisposed())
			display.dispose();
		
		reader.close();
		
	}

}

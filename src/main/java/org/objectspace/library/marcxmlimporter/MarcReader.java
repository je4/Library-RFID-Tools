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
package org.objectspace.library.marcxmlimporter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Juergen Enge
 *
 */
public class MarcReader extends DefaultHandler {

	/**
	 * @param config
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SQLException
	 * 
	 */
	public MarcReader(AbstractConfiguration config)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		sig_subfield = config.getString("marcreader.signature.subfield");
		datafield = config.getString("marcreader.datafield");
		code_subfield = config.getString("marcreader.barcode.subfield");
		title_datafield = config.getString("marcreader.title.datafield");
		title_subfield = config.getString("marcreader.title.subfield");

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
	}

	/**
	 * @param args
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws ConfigurationException
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public static void main(String[] args)
			throws ParserConfigurationException, SAXException, IOException, ConfigurationException,
			InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		String configfilename = "tagreader.xml";
		if (args.length > 0) {
			configfilename = args[0];
		}

		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(
				XMLConfiguration.class).configure(params.xml().setFileName(configfilename));

		XMLConfiguration config = builder.getConfiguration();

		String xmlfile = config.getString("marcreader.file", null);

		// Create a "parser factory" for creating SAX parsers
		SAXParserFactory spfac = SAXParserFactory.newInstance();

		// Now use the parser factory to create a SAXParser object
		SAXParser sp = spfac.newSAXParser();

		// Create an instance of this class; it defines all the handler methods
		MarcReader handler = new MarcReader(config);

		// HIL3/2$0030422 639.1 Hes MARC 954

		// Finally, tell the parser to parse the input and notify the handler
		sp.parse(xmlfile, handler);

	}

	/*
	 * When the parser encounters plain text (not XML elements), it calls(this
	 * method, which accumulates them in a string buffer
	 */
	public void characters(char[] buffer, int start, int length) {
		temp = new String(buffer, start, length);
	}

	/*
	 * Every time the parser encounters the beginning of a new element, it calls
	 * this method, which resets the string buffer
	 */
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		temp = "";
		if (qName.equalsIgnoreCase("record")) {
		} else if (qName.equalsIgnoreCase("datafield")) {
			currentDataField = attributes.getValue("tag");
			barcode = null;
			signatur = null;
		} else if (qName.equalsIgnoreCase("subfield")) {
			currentSubField = attributes.getValue("code");
		}
	}

	/*
	 * When the parser encounters the end of an element, it calls this method
	 */
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if (qName.equalsIgnoreCase("datafield")) {
			if (currentDataField.equalsIgnoreCase(datafield)) {
				if (barcode != null && signatur != null) {
					table.put(barcode, signatur);
				} else {
					System.out.println("Keine Signatur und Barcode: " + titel);
				}
			}
			barcode = null;
			signatur = null;
			titel = null;
		} else if (qName.equalsIgnoreCase("subfield")) {
			// if (currentDataField.equals("980") &&
			// currentSubField.equals("d")) {
			if (currentDataField.equals(datafield) && currentSubField.equals(sig_subfield)) {
				signatur = temp;
				// } else if (currentDataField.equals("984") &&
				// currentSubField.equals("a")) {
			} else if (currentDataField.equals(datafield) && currentSubField.equals(code_subfield)) {
				barcode = temp;
			} else if (currentDataField.equals(title_datafield) && currentSubField.equals(title_subfield)) {
				titel = temp;
			}

		}
	}

	public void startDocument() throws SAXException {
		table = new Hashtable<String, String>();
	}

	public void endDocument() throws SAXException {

		String insertSQL = "INSERT INTO `rfid`.`code_sig` " + "(`barcode`, `signatur`) VALUES (?, ?)";
		try {
			PreparedStatement stmt = conn.prepareStatement(insertSQL);

			int i = 1;
			System.out.println(String.format("\"%s\";\"%s\";\"%s\"", "Barcode", "Signatur", "Titel"));
			for (Map.Entry<String, String> entry : table.entrySet()) {
				// System.out.println(String.format("% 6d - %s: %s", i++,
				// entry.getKey(), entry.getValue()));
				System.out.println(String.format("%s;%s", entry.getKey(), entry.getValue()));
				stmt.setString(1, entry.getKey());
				stmt.setString(2, entry.getValue());
				int numRows = stmt.executeUpdate();
			}

			try {
				FileOutputStream fileout = new FileOutputStream("bc_sig.ser");
				ObjectOutputStream objout = new ObjectOutputStream(fileout);
				objout.writeObject(table);
				objout.close();
				fileout.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	protected String barcode = null;
	protected String signatur = null;
	protected String titel = null;
	private String temp = null;
	private Map<String, String> table = null;
	private String currentDataField = null;
	private String currentSubField = null;
	private String sig_subfield = null;
	private String title_datafield = null;
	private String title_subfield = null;
	private String datafield = null;
	private String code_subfield = null;

	protected static Connection conn = null;
}

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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Juergen Enge
 *
 */
public class MarcReader extends DefaultHandler {

	/**
	 * 
	 */
	public MarcReader() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 */
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		// Create a "parser factory" for creating SAX parsers
		SAXParserFactory spfac = SAXParserFactory.newInstance();

		// Now use the parser factory to create a SAXParser object
		SAXParser sp = spfac.newSAXParser();

		// Create an instance of this class; it defines all the handler methods
		MarcReader handler = new MarcReader();

		// HIL3/2$0030422 639.1 Hes MARC 954

		// Finally, tell the parser to parse the input and notify the handler
		sp.parse("hawk_91_2015-10-23.pp.xml", handler);

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
			barcode = null;
			signatur = null;
		} else if (qName.equalsIgnoreCase("datafield")) {
			currentDataField = attributes.getValue("tag");
		} else if (qName.equalsIgnoreCase("subfield")) {
			currentSubField = attributes.getValue("code");
		}
	}

	/*
	 * When the parser encounters the end of an element, it calls this method
	 */
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if (qName.equalsIgnoreCase("record")) {
			if (barcode != null && signatur != null) {
				table.put(barcode, signatur);
			}
			barcode = null;
			signatur = null;
		} else if (qName.equalsIgnoreCase("subfield")) {
			if (currentDataField.equals("980") && currentSubField.equals("d")) {
				signatur = temp;
			} else if (currentDataField.equals("984") && currentSubField.equals("a")) {
				barcode = temp;
			}

		}
	}

	public void startDocument() throws SAXException {
		table = new Hashtable<String, String>();
	}

	public void endDocument() throws SAXException {

		int i = 1;
		for (Map.Entry<String, String> entry : table.entrySet()) {
			System.out.println(String.format("% 6d - %s: %s", i++, entry.getKey(), entry.getValue()));
		}

		try {
			FileOutputStream fileout = new FileOutputStream("bc_sig.ser");
			ObjectOutputStream objout = new ObjectOutputStream( fileout );
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
	}

	protected String barcode = null;
	protected String signatur = null;
	private String temp = null;
	private Map<String, String> table = null;
	private String currentDataField = null;
	private String currentSubField = null;
}

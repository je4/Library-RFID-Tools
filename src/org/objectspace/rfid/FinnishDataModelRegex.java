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
package org.objectspace.rfid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.configuration2.AbstractConfiguration;

/**
 * 
 * @author Juergen Enge
 *
 */
public class FinnishDataModelRegex {

	/**
	 * 
	 */
	public FinnishDataModelRegex(AbstractConfiguration config, String field) {
		String baseconfig = "taghandle.autocorrect." + field.toLowerCase();
		pattern = new HashMap<String, Pattern>();
		Boolean active = config.getBoolean(baseconfig + ".active", false);
		if (active) {
			List<Object> matches = config.getList(baseconfig + ".matches.match.field");
			for (int i = 0; i < matches.size(); i++) {
				String name = config.getString(baseconfig + ".matches.match(" + i + ").field");
				String patternStr = config.getString(baseconfig + ".matches.match(" + i + ").pattern");
				Pattern p = Pattern.compile(patternStr);
				pattern.put(name, p);
			}
			replacement = config.getString(baseconfig + ".replacement");
		}

	}

	public String replace(FinnishDataModel fdm, String field) {
		String changed = fdm.getStringValue(field);
		for (Map.Entry<String, Pattern> e : pattern.entrySet()) {
			String fld = e.getKey();
			Pattern p = e.getValue();
			Matcher m = p.matcher(fdm.getStringValue(fld));
			if (!m.find()) {
				return fdm.getStringValue(field);
			}
			if (fld.equals(field))
				changed = m.replaceAll(replacement);
		}
		return changed;
	}

	HashMap<String, Pattern> pattern;
	String replacement;
}

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
package org.objectspace.rfid.feig.saveconfig;

import java.io.File;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.objectspace.rfid.feig.FeigRFID;

/**
 * Utility application to backup and restore firmware and reader configuration of FEIG RFID readers.
 * 
 * @author Juergen Enge
 *
 */
public class SaveConfig {

	/**
	 * default constructor
	 */
	public SaveConfig() {
	}

	/**
	 * Main entry point.
	 * 
	 * @param args &lt;get|backup|set|restore&gt; &lt;filename.xml&gt; [inventory.xml|host:port|tcp://host:port|DeviceId]
	 * @throws Exception on execution error
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("Verwendung: SaveConfig <get|backup|set|restore> <Dateiname.xml> [inventory.xml|host:port|tcp://host:port|DeviceId]");
			System.out.println("  get / backup:  Liest die Reader-/Firmware-Konfiguration aus und speichert sie als XML-Datei.");
			System.out.println("  set / restore: Schreibt eine gespeicherte XML-Konfiguration in den RFID-Leser zurück.");
			System.out.println("  Netzwerkziel:  host:port oder tcp://host:port (IPv6: tcp://[adresse]:port)");
			System.exit(1);
			return;
		}

		String action = args[0].trim().toLowerCase();
		String configfilename = args[1].trim();

		AbstractConfiguration config = null;

		if (args.length >= 3 && args[2] != null && !args[2].trim().isEmpty()) {
			config = configurationFromTarget(args[2]);
		} else {
			File defaultInventory = new File("inventory.xml");
			if (defaultInventory.exists() && defaultInventory.isFile()) {
				try {
					Parameters params = new Parameters();
					FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(
							XMLConfiguration.class).configure(params.xml().setFile(defaultInventory));
					config = builder.getConfiguration();
				} catch (Exception e) {
					// Fallback to default discovery if inventory.xml not parseable
				}
			}
		}

		FeigRFID feig = new FeigRFID(config);
		try {
			feig.connect();
			switch (action) {
			case "get":
			case "backup":
				File outFile = new File(configfilename).getAbsoluteFile();
				if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
					outFile.getParentFile().mkdirs();
				}
				System.out.println("Lese Konfiguration aus dem verbundenen RFID-Leser aus...");
				feig.copyConfigToFile(outFile.getAbsolutePath());
				System.out.println("Konfiguration erfolgreich gesichert in: " + outFile.getAbsolutePath());
				break;
			case "set":
			case "restore":
				File restoreFile = new File(configfilename).getAbsoluteFile();
				if (!restoreFile.exists() || !restoreFile.isFile()) {
					System.err.println("Fehler: Konfigurationsdatei '" + restoreFile.getAbsolutePath() + "' nicht gefunden!");
					System.exit(2);
					return;
				}
				System.out.println("Schreibe Konfiguration in den RFID-Leser aus: " + restoreFile.getAbsolutePath());
				feig.copyFileToConfig(restoreFile.getAbsolutePath());
				System.out.println("Warte, bis das EEPROM vollständig geschrieben ist...");
				feig.waitForEepromWrite(1500);
				System.out.println("Führe System-Reset (systemReset) auf dem RFID-Leser durch...");
				try {
					feig.systemReset();
					System.out.println("System-Reset erfolgreich durchgeführt.");
				} catch (Exception e) {
					System.err.println("Warnung beim Ausführen von systemReset: " + e.getMessage());
				}
				System.out.println("Konfiguration erfolgreich auf dem RFID-Leser wiederhergestellt.");
				break;
			default:
				System.err.println("Unbekannte Aktion: " + action + " (Erwartet: get/backup oder set/restore)");
				System.exit(1);
			}
		} finally {
			try {
				feig.close();
			} catch (Throwable t) {
				// Ignore close errors
			}
		}
	}

	static AbstractConfiguration configurationFromTarget(String target) throws Exception {
		if (target == null || target.trim().isEmpty()) {
			throw new IllegalArgumentException("Reader-Ziel darf nicht leer sein");
		}
		String value = target.trim();
		File customFile = new File(value);
		if (customFile.exists() && customFile.isFile()) {
			try {
				Parameters params = new Parameters();
				FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(
						XMLConfiguration.class).configure(params.xml().setFile(customFile));
				return builder.getConfiguration();
			} catch (Exception e) {
				throw new IllegalArgumentException("Konnte Konfigurationsdatei '" + value + "' nicht laden: " + e.getMessage(), e);
			}
		}

		TcpTarget tcpTarget = parseTcpTarget(value);
		BaseConfiguration baseConfig = new BaseConfiguration();
		if (tcpTarget != null) {
			baseConfig.setProperty("device.feig.type", "ethernet");
			baseConfig.setProperty("device.feig.host", tcpTarget.host);
			baseConfig.setProperty("device.feig.port", tcpTarget.port);
		} else {
			baseConfig.setProperty("device.feig.id", value);
			baseConfig.setProperty("device.feig.type", "usb");
		}
		return baseConfig;
	}

	private static TcpTarget parseTcpTarget(String value) {
		boolean explicitTcp = value.regionMatches(true, 0, "tcp://", 0, 6);
		String address = explicitTcp ? value.substring(6) : value;
		if (!explicitTcp && address.indexOf(':') < 0) {
			return null;
		}

		String host;
		String portValue;
		if (address.startsWith("[")) {
			int closeBracket = address.indexOf(']');
			if (closeBracket <= 1 || closeBracket + 1 >= address.length() || address.charAt(closeBracket + 1) != ':') {
				throw invalidTcpTarget(value);
			}
			host = address.substring(1, closeBracket);
			portValue = address.substring(closeBracket + 2);
		} else {
			int separator = address.lastIndexOf(':');
			if (separator <= 0 || separator != address.indexOf(':')) {
				throw invalidTcpTarget(value);
			}
			host = address.substring(0, separator);
			portValue = address.substring(separator + 1);
		}
		if (host.isBlank() || !host.equals(host.trim()) || host.chars().anyMatch(Character::isWhitespace)
				|| host.contains("/") || host.contains("?") || host.contains("#") || host.contains("@")
				|| portValue.isBlank() || portValue.contains("/") || portValue.contains("?") || portValue.contains("#")) {
			throw invalidTcpTarget(value);
		}
		int port;
		try {
			if (!portValue.matches("[0-9]+")) {
				throw invalidTcpTarget(value);
			}
			port = Integer.parseInt(portValue);
		} catch (NumberFormatException e) {
			throw invalidTcpTarget(value);
		}
		if (port < 1 || port > 65535) {
			throw invalidTcpTarget(value);
		}
		return new TcpTarget(host, port);
	}

	private static IllegalArgumentException invalidTcpTarget(String value) {
		return new IllegalArgumentException("Ungültiges TCP-Ziel '" + value
				+ "'. Erwartet wird host:port oder tcp://host:port (IPv6: tcp://[adresse]:port)");
	}

	private static final class TcpTarget {
		private final String host;
		private final int port;

		private TcpTarget(String host, int port) {
			this.host = host;
			this.port = port;
		}
	}
}

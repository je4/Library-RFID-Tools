/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
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
 *******************************************************************************/
package org.objectspace.rfid.webservice;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.objectspace.rfid.FinnishDataModel;

/**
 * Builds target URLs containing marker, session, and RFID tag content.
 * Compatible with the Android NFC Reader application's UrlBuilder.
 */
public class UrlBuilder {

	private UrlBuilder() {
	}

	/**
	 * Builds the target URL containing the marker, session, and NFC/RFID content.
	 * Supports template placeholders ({marker}, {session}, {text}, {raw}, {uid}, {timestamp}, {ts}, {jwt},
	 * {itemid}, {afi}, {country}, {isil}, {parts}, {partno}, {usagetype}, {version}) and
	 * automatic query parameter appending if placeholders are not present (for GET requests).
	 * For POST requests, query parameters are omitted.
	 *
	 * @param baseUrlOrTemplate The base URL or template with placeholders
	 * @param marker            The marker / shelf identifier
	 * @param rawPayloadHex     Raw tag payload as hex string
	 * @param uid               The tag UID
	 * @param timestamp         Scan timestamp in ms
	 * @param jwtToken          JWT token string (optional)
	 * @param libraryData       Parsed FinnishDataModel (optional)
	 * @param nfcContent        Text representation of tag content (optional)
	 * @param session           Session name (optional, generated if blank)
	 * @param deviceName        Device name (optional, determined from system if blank)
	 * @param httpMethod        "GET" or "POST"
	 * @param userText          User text / marker fallback (optional)
	 * @return The formatted URL
	 */
	public static String buildUrl(
			String baseUrlOrTemplate,
			String marker,
			String rawPayloadHex,
			String uid,
			long timestamp,
			String jwtToken,
			FinnishDataModel libraryData,
			String nfcContent,
			String session,
			String deviceName,
			String httpMethod,
			String userText
	) {
		if (baseUrlOrTemplate == null) {
			return "";
		}
		String trimmed = baseUrlOrTemplate.trim();
		if (trimmed.isEmpty()) {
			return "";
		}

		String effectiveMarker = (marker != null && !marker.trim().isEmpty()) ? marker.trim() : (userText != null ? userText.trim() : "");
		String rawValue = (rawPayloadHex != null && !rawPayloadHex.trim().isEmpty()) ? rawPayloadHex.trim() : (nfcContent != null ? nfcContent.trim() : "");
		String actualSession = (session != null && !session.trim().isEmpty()) ? session.trim() : generateSession(deviceName, timestamp);

		String encodedMarker = encode(effectiveMarker);
		String encodedSession = encode(actualSession);
		String encodedRaw = encode(rawValue);
		String encodedUid = encode(uid != null ? uid.trim() : "");
		String encodedTimestamp = encode(Long.toString(timestamp));
		String encodedJwt = encode(jwtToken != null ? jwtToken.trim() : "");

		String itemId = (libraryData != null && libraryData.getPrimaryItemId() != null) ? libraryData.getPrimaryItemId() : "";
		String country = (libraryData != null && libraryData.getCountryOfOwnerLib() != null) ? libraryData.getCountryOfOwnerLib() : "";
		String isil = (libraryData != null && libraryData.getISIL() != null) ? libraryData.getISIL() : "";
		String parts = (libraryData != null) ? Integer.toString(libraryData.getPartsInItem()) : "";
		String partNo = (libraryData != null) ? Integer.toString(libraryData.getPartNumber()) : "";
		String usageType = (libraryData != null) ? Integer.toString(libraryData.getTypeOfUsage()) : "";
		String version = (libraryData != null) ? Integer.toString(libraryData.getVersion()) : "";
		String afi = "";

		String encodedItemId = encode(itemId);
		String encodedCountry = encode(country);
		String encodedIsil = encode(isil);
		String encodedParts = encode(parts);
		String encodedPartNo = encode(partNo);
		String encodedUsageType = encode(usageType);
		String encodedVersion = encode(version);
		String encodedAfi = encode(afi);

		if (httpMethod != null && httpMethod.equalsIgnoreCase("POST")) {
			// Beim POST-Aufruf sollen keine GET-Parameter vorhanden sein
			String pathPart = trimmed.contains("?") ? trimmed.substring(0, trimmed.indexOf("?")) : trimmed;
			return pathPart
					.replace("{marker}", encodedMarker)
					.replace("{text}", encodedMarker)
					.replace("{session}", encodedSession)
					.replace("{raw}", encodedRaw)
					.replace("{uid}", encodedUid)
					.replace("{timestamp}", encodedTimestamp)
					.replace("{ts}", encodedTimestamp)
					.replace("{jwt}", encodedJwt)
					.replace("{itemid}", encodedItemId)
					.replace("{afi}", encodedAfi)
					.replace("{country}", encodedCountry)
					.replace("{isil}", encodedIsil)
					.replace("{parts}", encodedParts)
					.replace("{partno}", encodedPartNo)
					.replace("{usagetype}", encodedUsageType)
					.replace("{version}", encodedVersion);
		}

		boolean hasPlaceholders = trimmed.contains("{marker}") ||
				trimmed.contains("{text}") ||
				trimmed.contains("{session}") ||
				trimmed.contains("{raw}") ||
				trimmed.contains("{uid}") ||
				trimmed.contains("{timestamp}") ||
				trimmed.contains("{ts}") ||
				trimmed.contains("{jwt}") ||
				trimmed.contains("{itemid}") ||
				trimmed.contains("{afi}") ||
				trimmed.contains("{country}") ||
				trimmed.contains("{isil}") ||
				trimmed.contains("{parts}") ||
				trimmed.contains("{partno}") ||
				trimmed.contains("{usagetype}") ||
				trimmed.contains("{version}");

		if (hasPlaceholders) {
			return trimmed
					.replace("{marker}", encodedMarker)
					.replace("{text}", encodedMarker)
					.replace("{session}", encodedSession)
					.replace("{raw}", encodedRaw)
					.replace("{uid}", encodedUid)
					.replace("{timestamp}", encodedTimestamp)
					.replace("{ts}", encodedTimestamp)
					.replace("{jwt}", encodedJwt)
					.replace("{itemid}", encodedItemId)
					.replace("{afi}", encodedAfi)
					.replace("{country}", encodedCountry)
					.replace("{isil}", encodedIsil)
					.replace("{parts}", encodedParts)
					.replace("{partno}", encodedPartNo)
					.replace("{usagetype}", encodedUsageType)
					.replace("{version}", encodedVersion);
		} else {
			String delimiter = trimmed.contains("?") ? "&" : "?";
			String baseParams = trimmed + delimiter + "marker=" + encodedMarker + "&session=" + encodedSession +
					"&raw=" + encodedRaw + "&uid=" + encodedUid + "&ts=" + encodedTimestamp;
			if (libraryData != null && !libraryData.isEmpty()) {
				String afiParam = (encodedAfi != null && !encodedAfi.isEmpty()) ? "&afi=" + encodedAfi : "";
				return baseParams + "&itemid=" + encodedItemId + "&country=" + encodedCountry +
						"&isil=" + encodedIsil + "&parts=" + encodedParts + "&partno=" + encodedPartNo +
						"&usagetype=" + encodedUsageType + "&version=" + encodedVersion + afiParam;
			} else {
				return baseParams;
			}
		}
	}

	/**
	 * Creates the session string consisting of device name and current date in format yyyyMMdd.
	 *
	 * @param deviceName Optional device name
	 * @param timestamp  Scan timestamp in ms
	 * @return Session identifier
	 */
	public static String generateSession(String deviceName, long timestamp) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
		String dateStr = dateFormat.format(new Date(timestamp));
		String device = (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName.trim() : getDeviceName();
		if (device != null && !device.isEmpty()) {
			return device + "_" + dateStr;
		} else {
			return dateStr;
		}
	}

	/**
	 * Determines a local device identifier name.
	 *
	 * @return Device identifier string
	 */
	public static String getDeviceName() {
		try {
			String host = System.getenv("COMPUTERNAME");
			if (host != null && !host.trim().isEmpty()) {
				return host.trim();
			}
			host = System.getenv("HOSTNAME");
			if (host != null && !host.trim().isEmpty()) {
				return host.trim();
			}
			InetAddress addr = InetAddress.getLocalHost();
			if (addr != null && addr.getHostName() != null && !addr.getHostName().isEmpty()) {
				return addr.getHostName().trim();
			}
		} catch (Throwable t) {
			// Ignore
		}
		return "Desktop";
	}

	private static String encode(String value) {
		if (value == null) {
			return "";
		}
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return value;
		}
	}
}

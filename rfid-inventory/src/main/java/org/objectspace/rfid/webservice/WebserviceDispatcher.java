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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.objectspace.rfid.FinnishDataModel;

/**
 * Dispatches scanned RFID tag data to the configured HTTP/HTTPS webservice endpoint.
 * Fully compatible with the Android NFC Reader application's UrlDispatcher.
 */
public class WebserviceDispatcher {

	private final WebserviceConfig config;
	private final HttpClient httpClient;

	public WebserviceDispatcher(WebserviceConfig config) {
		this(config, HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build());
	}

	public WebserviceDispatcher(WebserviceConfig config, HttpClient httpClient) {
		this.config = (config != null) ? config : new WebserviceConfig();
		this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	public WebserviceConfig getConfig() {
		return config;
	}

	/**
	 * Prepares and sends a scan dispatch request to the webservice.
	 *
	 * @param marker        Marker or location label
	 * @param userText      User text (fallback for marker)
	 * @param nfcContent    Text representation of tag
	 * @param rawPayloadHex Hexadecimal raw payload
	 * @param uid           UID of RFID tag
	 * @param libraryData   Parsed FinnishDataModel (optional)
	 * @param session       Session identifier (optional)
	 * @param deviceName    Device name (optional)
	 * @return WebserviceResponse containing status code, response body, and debug details
	 */
	public WebserviceResponse dispatchScan(
			String marker,
			String userText,
			String nfcContent,
			String rawPayloadHex,
			String uid,
			FinnishDataModel libraryData,
			String session,
			String deviceName
	) {
		long timestamp = System.currentTimeMillis();
		String targetUrlTemplate = config.getTargetUrl();
		String httpMethod = config.getHttpMethod();
		String jwtKey = config.getJwtKey();

		String jwtToken = null;
		if (jwtKey != null && !jwtKey.trim().isEmpty()) {
			try {
				jwtToken = JwtGenerator.generateToken(jwtKey, 60, timestamp, null);
			} catch (Exception e) {
				System.err.println("JWT token generation failed: " + e.getMessage());
			}
		}

		String actualMarker = (marker != null && !marker.trim().isEmpty()) ? marker.trim() : (userText != null ? userText.trim() : "");
		String actualSession = (session != null && !session.trim().isEmpty()) ? session.trim() : UrlBuilder.generateSession(deviceName, timestamp);

		String fullUrl = UrlBuilder.buildUrl(
				targetUrlTemplate,
				actualMarker,
				rawPayloadHex,
				uid,
				timestamp,
				jwtToken != null ? jwtToken : "",
				libraryData,
				nfcContent,
				actualSession,
				deviceName,
				httpMethod,
				userText
		);

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", "Iso15693NfcReader/1.0 (Desktop)");
		headers.put("Accept", "application/json, text/plain, */*");

		if (jwtToken != null && !jwtToken.trim().isEmpty()) {
			String authHeader = jwtToken.startsWith("Bearer ") ? jwtToken : "Bearer " + jwtToken;
			headers.put("Authorization", authHeader);
		}

		String requestBody = null;
		if (httpMethod.equalsIgnoreCase("POST")) {
			requestBody = buildJsonBody(actualMarker, actualSession, rawPayloadHex, nfcContent, uid, timestamp, jwtToken, libraryData);
			headers.put("Content-Type", "application/json; charset=utf-8");
		}

		if (fullUrl == null || fullUrl.trim().isEmpty()) {
			return WebserviceResponse.failure(
					0,
					"Target URL is empty or invalid",
					targetUrlTemplate != null ? targetUrlTemplate : "",
					0,
					jwtKey,
					headers,
					requestBody,
					"Target URL is empty"
			);
		}

		HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
				.uri(URI.create(fullUrl))
				.timeout(Duration.ofSeconds(10));

		for (Map.Entry<String, String> entry : headers.entrySet()) {
			reqBuilder.header(entry.getKey(), entry.getValue());
		}

		if (httpMethod.equalsIgnoreCase("POST")) {
			reqBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
		} else {
			reqBuilder.GET();
		}

		HttpRequest request = reqBuilder.build();
		long startTime = System.currentTimeMillis();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			long durationMs = System.currentTimeMillis() - startTime;
			int statusCode = response.statusCode();
			String responseBody = response.body() != null ? response.body() : "";
			boolean isSuccess = (statusCode >= 200 && statusCode < 300);

			String debugString = formatHttpRequestDebug(httpMethod, fullUrl, headers, requestBody, statusCode, responseBody, durationMs);

			if (config.isDebugMode()) {
				System.out.println("=== HTTP REQUEST (DEBUG) ===\n" + debugString);
			}

			if (isSuccess) {
				return WebserviceResponse.success(
						statusCode,
						responseBody,
						fullUrl,
						durationMs,
						jwtKey,
						headers,
						requestBody,
						debugString
				);
			} else {
				return WebserviceResponse.failure(
						statusCode,
						responseBody,
						"HTTP error " + statusCode + ": " + responseBody,
						fullUrl,
						durationMs,
						jwtKey,
						headers,
						requestBody,
						debugString
				);
			}
		} catch (Exception e) {
			long durationMs = System.currentTimeMillis() - startTime;
			String debugString = formatHttpRequestDebug(httpMethod, fullUrl, headers, requestBody, 0, e.getMessage(), durationMs);
			if (config.isDebugMode()) {
				System.err.println("=== HTTP REQUEST FAILED (DEBUG) ===\n" + debugString + "\nException: " + e.getMessage());
			}
			return WebserviceResponse.failure(
					0,
					e.getMessage(),
					fullUrl,
					durationMs,
					jwtKey,
					headers,
					requestBody,
					debugString
			);
		}
	}

	private String buildJsonBody(
			String marker,
			String session,
			String rawPayloadHex,
			String nfcContent,
			String uid,
			long timestamp,
			String jwtToken,
			FinnishDataModel libraryData
	) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"marker\":\"").append(escapeJson(marker)).append("\",");
		sb.append("\"session\":\"").append(escapeJson(session)).append("\",");
		String raw = (rawPayloadHex != null && !rawPayloadHex.trim().isEmpty()) ? rawPayloadHex : (nfcContent != null ? nfcContent : "");
		sb.append("\"raw\":\"").append(escapeJson(raw)).append("\",");
		sb.append("\"uid\":\"").append(escapeJson(uid != null ? uid : "")).append("\",");
		sb.append("\"timestamp\":").append(timestamp);

		if (jwtToken != null && !jwtToken.trim().isEmpty()) {
			sb.append(",\"jwt\":\"").append(escapeJson(jwtToken)).append("\"");
		}

		if (libraryData != null && !libraryData.isEmpty()) {
			if (libraryData.getPrimaryItemId() != null) {
				sb.append(",\"itemId\":\"").append(escapeJson(libraryData.getPrimaryItemId())).append("\"");
			}
			if (libraryData.getCountryOfOwnerLib() != null) {
				sb.append(",\"country\":\"").append(escapeJson(libraryData.getCountryOfOwnerLib())).append("\"");
			}
			if (libraryData.getISIL() != null) {
				sb.append(",\"isil\":\"").append(escapeJson(libraryData.getISIL())).append("\"");
			}
			sb.append(",\"parts\":").append(libraryData.getPartsInItem());
			sb.append(",\"partNo\":").append(libraryData.getPartNumber());
			sb.append(",\"usageType\":").append(libraryData.getTypeOfUsage());
			sb.append(",\"version\":").append(libraryData.getVersion());
			sb.append(",\"isCrcValid\":").append(!libraryData.getCRCError());
		}

		sb.append("}");
		return sb.toString();
	}

	private String formatHttpRequestDebug(
			String method,
			String url,
			Map<String, String> headers,
			String body,
			int statusCode,
			String responseBody,
			long durationMs
	) {
		StringBuilder sb = new StringBuilder();
		sb.append("Method: ").append(method).append("\n");
		sb.append("URL: ").append(url).append("\n");
		sb.append("Headers:\n");
		for (Map.Entry<String, String> h : headers.entrySet()) {
			sb.append("  ").append(h.getKey()).append(": ").append(h.getValue()).append("\n");
		}
		if (body != null && !body.isEmpty()) {
			sb.append("Request Body:\n  ").append(body).append("\n");
		}
		sb.append("Response Code: ").append(statusCode).append("\n");
		if (responseBody != null && !responseBody.isEmpty()) {
			sb.append("Response Body:\n  ").append(responseBody).append("\n");
		}
		sb.append("Duration: ").append(durationMs).append(" ms");
		return sb.toString();
	}

	private static String escapeJson(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (ch < ' ') {
						String hex = String.format("%04x", (int) ch);
						sb.append("\\u").append(hex);
					} else {
						sb.append(ch);
					}
					break;
			}
		}
		return sb.toString();
	}
}

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

import java.util.Collections;
import java.util.Map;

/**
 * Result data class for an executed Webservice scan request.
 * Compatible with Android NFC Reader's ScanResponse.
 */
public class WebserviceResponse {

	private final int httpStatus;
	private final String responseBody;
	private final String requestUrl;
	private final long durationMs;
	private final boolean success;
	private final String errorMessage;
	private final String jwtKey;
	private final Map<String, String> requestHeaders;
	private final String requestBody;
	private final String httpRequestDebug;

	public WebserviceResponse(
			int httpStatus,
			String responseBody,
			String requestUrl,
			long durationMs,
			boolean success,
			String errorMessage,
			String jwtKey,
			Map<String, String> requestHeaders,
			String requestBody,
			String httpRequestDebug
	) {
		this.httpStatus = httpStatus;
		this.responseBody = responseBody != null ? responseBody : "";
		this.requestUrl = requestUrl != null ? requestUrl : "";
		this.durationMs = durationMs;
		this.success = success;
		this.errorMessage = errorMessage;
		this.jwtKey = jwtKey;
		this.requestHeaders = requestHeaders != null ? requestHeaders : Collections.emptyMap();
		this.requestBody = requestBody;
		this.httpRequestDebug = httpRequestDebug;
	}

	public static WebserviceResponse success(
			int httpStatus,
			String responseBody,
			String requestUrl,
			long durationMs,
			String jwtKey,
			Map<String, String> requestHeaders,
			String requestBody,
			String httpRequestDebug
	) {
		return new WebserviceResponse(httpStatus, responseBody, requestUrl, durationMs, true, null, jwtKey, requestHeaders, requestBody, httpRequestDebug);
	}

	public static WebserviceResponse failure(
			int httpStatus,
			String errorMessage,
			String requestUrl,
			long durationMs,
			String jwtKey,
			Map<String, String> requestHeaders,
			String requestBody,
			String httpRequestDebug
	) {
		return new WebserviceResponse(httpStatus, "", requestUrl, durationMs, false, errorMessage, jwtKey, requestHeaders, requestBody, httpRequestDebug);
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public String getRequestUrl() {
		return requestUrl;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getJwtKey() {
		return jwtKey;
	}

	public Map<String, String> getRequestHeaders() {
		return requestHeaders;
	}

	public String getRequestBody() {
		return requestBody;
	}

	public String getHttpRequestDebug() {
		return httpRequestDebug;
	}

	@Override
	public String toString() {
		return "WebserviceResponse{" +
				"httpStatus=" + httpStatus +
				", success=" + success +
				", durationMs=" + durationMs +
				", requestUrl='" + requestUrl + '\'' +
				", errorMessage='" + errorMessage + '\'' +
				'}';
	}
}

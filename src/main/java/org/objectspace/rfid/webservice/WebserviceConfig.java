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

import org.apache.commons.configuration2.AbstractConfiguration;

/**
 * Configuration settings for the RFID Webservice integration.
 * Mirrors the settings from the Android NFC Reader application.
 */
public class WebserviceConfig {

	public static final String DEFAULT_TARGET_URL = "https://httpbin.org/get";
	public static final String DEFAULT_HTTP_METHOD = "GET";

	private boolean active = false;
	private String targetUrl = DEFAULT_TARGET_URL;
	private String httpMethod = DEFAULT_HTTP_METHOD;
	private String jwtKey = "";
	private boolean debugMode = false;

	public WebserviceConfig() {
	}

	public WebserviceConfig(boolean active, String targetUrl, String httpMethod, String jwtKey, boolean debugMode) {
		this.active = active;
		this.targetUrl = (targetUrl != null && !targetUrl.trim().isEmpty()) ? targetUrl.trim() : DEFAULT_TARGET_URL;
		this.httpMethod = (httpMethod != null && !httpMethod.trim().isEmpty()) ? httpMethod.trim().toUpperCase() : DEFAULT_HTTP_METHOD;
		this.jwtKey = (jwtKey != null) ? jwtKey.trim() : "";
		this.debugMode = debugMode;
	}

	/**
	 * Parses Webservice configuration parameters from the application configuration.
	 * Supports standard keys as well as common aliases.
	 *
	 * @param config AbstractConfiguration
	 * @return WebserviceConfig instance
	 */
	public static WebserviceConfig fromConfiguration(AbstractConfiguration config) {
		WebserviceConfig wsConfig = new WebserviceConfig();
		if (config == null) {
			return wsConfig;
		}

		wsConfig.setActive(config.getBoolean("webservice.active", false));

		String url = config.getString("webservice.target_url");
		if (url == null) {
			url = config.getString("webservice.url");
		}
		if (url == null) {
			url = config.getString("webservice.targeturl");
		}
		if (url != null && !url.trim().isEmpty()) {
			wsConfig.setTargetUrl(url.trim());
		}

		String method = config.getString("webservice.http_method");
		if (method == null) {
			method = config.getString("webservice.method");
		}
		if (method == null) {
			method = config.getString("webservice.httpmethod");
		}
		if (method != null && !method.trim().isEmpty()) {
			wsConfig.setHttpMethod(method.trim().toUpperCase());
		}

		String jwt = config.getString("webservice.jwt_key");
		if (jwt == null) {
			jwt = config.getString("webservice.jwtkey");
		}
		if (jwt == null) {
			jwt = config.getString("webservice.jwt");
		}
		if (jwt != null) {
			wsConfig.setJwtKey(jwt.trim());
		}

		boolean debug = config.getBoolean("webservice.debug_mode", config.getBoolean("webservice.debug", false));
		wsConfig.setDebugMode(debug);

		return wsConfig;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getTargetUrl() {
		return targetUrl;
	}

	public void setTargetUrl(String targetUrl) {
		this.targetUrl = (targetUrl != null && !targetUrl.trim().isEmpty()) ? targetUrl.trim() : DEFAULT_TARGET_URL;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	public void setHttpMethod(String httpMethod) {
		this.httpMethod = (httpMethod != null && !httpMethod.trim().isEmpty()) ? httpMethod.trim().toUpperCase() : DEFAULT_HTTP_METHOD;
	}

	public String getJwtKey() {
		return jwtKey;
	}

	public void setJwtKey(String jwtKey) {
		this.jwtKey = (jwtKey != null) ? jwtKey.trim() : "";
	}

	public boolean isDebugMode() {
		return debugMode;
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}

	@Override
	public String toString() {
		return "WebserviceConfig{" +
				"active=" + active +
				", targetUrl='" + targetUrl + '\'' +
				", httpMethod='" + httpMethod + '\'' +
				", jwtKey='" + (jwtKey.isEmpty() ? "(none)" : "***") + '\'' +
				", debugMode=" + debugMode +
				'}';
	}
}

package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data representing RFID hardware device metadata response:
 * - Status ("ok" / "error")
 * - Device ID
 * - Device Type
 * - Komplettbeschreibung aus dem init (complete init description / startup report)
 * - Reader ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceMetadataData(
		@JsonProperty("status") String status,
		@JsonProperty("deviceId") @JsonAlias({"deviceID", "Device ID", "device_id"}) String deviceId,
		@JsonProperty("deviceType") @JsonAlias({"Device Type", "device_type"}) String deviceType,
		@JsonProperty("initDescription") @JsonAlias({"description", "completeDescription", "Komplettbeschreibung aus dem init", "init_description"}) String initDescription,
		@JsonProperty("readerId") String readerId
) {
	public static final String EVENT_NAME = "rfid:device_metadata";
	public static final String EVENT_RESPONSE = "rfid:device_metadata_response";

	public DeviceMetadataData {
		status = (status != null && !status.isBlank()) ? status : "ok";
		deviceId = deviceId != null ? deviceId : "";
		deviceType = deviceType != null ? deviceType : "";
		initDescription = initDescription != null ? initDescription : "";
		readerId = (readerId != null && !readerId.isBlank()) ? readerId : null;
	}

	public static DeviceMetadataData of(String deviceId, String deviceType, String initDescription, String readerId, String status) {
		return new DeviceMetadataData(status, deviceId, deviceType, initDescription, readerId);
	}

	public static DeviceMetadataData of(String deviceId, String deviceType, String initDescription, String readerId) {
		return of(deviceId, deviceType, initDescription, readerId, "ok");
	}

	public static DeviceMetadataData of(String deviceId, String deviceType, String initDescription) {
		return of(deviceId, deviceType, initDescription, null, "ok");
	}

	public DeviceMetadataData(String deviceId, String deviceType, String initDescription, String readerId) {
		this("ok", deviceId, deviceType, initDescription, readerId);
	}

	public DeviceMetadataData(String deviceId, String deviceType, String initDescription) {
		this("ok", deviceId, deviceType, initDescription, null);
	}

	@JsonProperty("description")
	public String getDescription() {
		return initDescription;
	}

	@JsonIgnore
	public String getSummaryMessage() {
		return String.format("[DEVICE METADATA] Status: %s | ID: %s | Type: %s | Description: %s",
				status, deviceId, deviceType,
				(initDescription != null && initDescription.length() > 60 ? initDescription.substring(0, 57) + "..." : initDescription));
	}
}

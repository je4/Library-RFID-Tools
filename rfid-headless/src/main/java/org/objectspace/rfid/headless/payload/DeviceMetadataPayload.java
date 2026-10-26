package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload representing RFID hardware device metadata response:
 * - Status ("ok")
 * - Device ID
 * - Device Type
 * - Komplettbeschreibung aus dem init (complete init description / startup report)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceMetadataPayload extends BaseRfidPayload {

	public static final String EVENT_NAME = "rfid:device_metadata";
	public static final String EVENT_RESPONSE = "rfid:device_metadata_response";

	@JsonProperty("status")
	private final String status;

	@JsonProperty("deviceId")
	@JsonAlias({"deviceID", "Device ID", "device_id"})
	private final String deviceId;

	@JsonProperty("deviceType")
	@JsonAlias({"Device Type", "device_type"})
	private final String deviceType;

	@JsonProperty("initDescription")
	@JsonAlias({"description", "completeDescription", "Komplettbeschreibung aus dem init", "init_description"})
	private final String initDescription;

	public DeviceMetadataPayload(String deviceId, String deviceType, String initDescription,
								 String sessionName, long timestamp, String clientId, String status, String event) {
		this(deviceId, deviceType, initDescription, sessionName, timestamp, clientId, clientId, status, event);
	}

	public DeviceMetadataPayload(String deviceId, String deviceType, String initDescription,
								 String sessionName, long timestamp, String clientId, String readerId, String status, String event) {
		super(event != null && !event.isBlank() ? event : EVENT_RESPONSE, sessionName, timestamp, clientId, readerId);
		this.deviceId = deviceId != null ? deviceId : "";
		this.deviceType = deviceType != null ? deviceType : "";
		this.initDescription = initDescription != null ? initDescription : "";
		this.status = status != null ? status : "ok";
	}

	public DeviceMetadataPayload(String deviceId, String deviceType, String initDescription,
								 String sessionName, long timestamp, String clientId) {
		this(deviceId, deviceType, initDescription, sessionName, timestamp, clientId, clientId, "ok", EVENT_RESPONSE);
	}

	public DeviceMetadataPayload(String deviceId, String deviceType, String initDescription,
								 String sessionName, long timestamp, String clientId, String readerId) {
		this(deviceId, deviceType, initDescription, sessionName, timestamp, clientId, readerId, "ok", EVENT_RESPONSE);
	}

	public DeviceMetadataPayload(String deviceId, String deviceType, String initDescription) {
		this(deviceId, deviceType, initDescription, "", System.currentTimeMillis(), "", "", "ok", EVENT_RESPONSE);
	}

	public String getStatus() {
		return status;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getDeviceType() {
		return deviceType;
	}

	public String getInitDescription() {
		return initDescription;
	}

	@JsonProperty("description")
	public String getDescription() {
		return initDescription;
	}

	@JsonIgnore
	@Override
	public String getSummaryMessage() {
		return String.format("[DEVICE METADATA] Status: %s | ID: %s | Type: %s | Description: %s",
				status, deviceId, deviceType,
				(initDescription != null && initDescription.length() > 60 ? initDescription.substring(0, 57) + "..." : initDescription));
	}
}

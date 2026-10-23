package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload representing RFID tag AFI write response:
 * - Status ("ok" / "error")
 * - UID
 * - AFI (written AFI byte formatted as hex string or decimal)
 * - Message / Error description
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WriteAfiResponsePayload extends BaseRfidPayload {

	public static final String EVENT_RESPONSE = "rfid:write_afi_response";

	@JsonProperty("status")
	private final String status;

	@JsonProperty("uid")
	@JsonAlias({"UID"})
	private final String uid;

	@JsonProperty("afi")
	@JsonAlias({"AFI"})
	private final String afi;

	@JsonProperty("message")
	private final String message;

	@JsonProperty("error")
	private final String error;

	public WriteAfiResponsePayload(String status, String uid, String afi,
								   String message, String error, String sessionName,
								   long timestamp, String clientId) {
		super(EVENT_RESPONSE, sessionName, timestamp, clientId);
		this.status = status != null ? status : "ok";
		this.uid = uid != null ? uid : "";
		this.afi = afi != null ? afi : "";
		this.message = message;
		this.error = error;
	}

	public static WriteAfiResponsePayload success(String uid, String afi, String sessionName, String clientId) {
		return new WriteAfiResponsePayload(
				"ok", uid, afi, "AFI written successfully", null, sessionName,
				System.currentTimeMillis(), clientId
		);
	}

	public static WriteAfiResponsePayload error(String uid, String afi, String errorMessage,
												String sessionName, String clientId) {
		return new WriteAfiResponsePayload(
				"error", uid, afi, errorMessage, errorMessage, sessionName,
				System.currentTimeMillis(), clientId
		);
	}

	public String getStatus() {
		return status;
	}

	public String getUid() {
		return uid;
	}

	public String getAfi() {
		return afi;
	}

	public String getMessage() {
		return message;
	}

	public String getError() {
		return error;
	}

	public boolean isSuccess() {
		return "ok".equalsIgnoreCase(status);
	}

	@JsonIgnore
	@Override
	public String getSummaryMessage() {
		if (isSuccess()) {
			return String.format("[WRITE AFI] Status: OK | UID: %s | AFI: %s", uid, afi);
		} else {
			return String.format("[WRITE AFI ERROR] UID: %s | Error: %s", uid, (error != null ? error : message));
		}
	}
}

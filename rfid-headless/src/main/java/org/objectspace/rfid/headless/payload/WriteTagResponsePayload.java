package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload representing RFID tag write response:
 * - Status ("ok" / "error")
 * - UID
 * - Item ID
 * - ISIL
 * - Country
 * - Parts
 * - Part number
 * - Usage type
 * - AFI (if written)
 * - Message / Error description
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WriteTagResponsePayload extends BaseRfidPayload {

	public static final String EVENT_RESPONSE = "rfid:write_tag_response";

	@JsonProperty("status")
	private final String status;

	@JsonProperty("uid")
	@JsonAlias({"UID"})
	private final String uid;

	@JsonProperty("itemId")
	@JsonAlias({"imtemId", "primaryItemId", "item_id", "barcode"})
	private final String itemId;

	@JsonProperty("isil")
	@JsonAlias({"ISIL"})
	private final String isil;

	@JsonProperty("country")
	@JsonAlias({"countryOfOwnerLib"})
	private final String country;

	@JsonProperty("parts")
	private final int parts;

	@JsonProperty("partNo")
	@JsonAlias({"partno", "partNumber"})
	private final int partNo;

	@JsonProperty("usageType")
	@JsonAlias({"typeOfUsage"})
	private final int usageType;

	@JsonProperty("afi")
	@JsonAlias({"AFI"})
	private final String afi;

	@JsonProperty("message")
	private final String message;

	@JsonProperty("error")
	private final String error;

	public WriteTagResponsePayload(String status, String uid, String itemId, String isil, String country,
								   int parts, int partNo, int usageType, String afi,
								   String message, String error, String sessionName,
								   long timestamp, String clientId) {
		super(EVENT_RESPONSE, sessionName, timestamp, clientId);
		this.status = status != null ? status : "ok";
		this.uid = uid != null ? uid : "";
		this.itemId = itemId != null ? itemId : "";
		this.isil = isil != null ? isil : "";
		this.country = country != null ? country : "";
		this.parts = parts;
		this.partNo = partNo;
		this.usageType = usageType;
		this.afi = afi;
		this.message = message;
		this.error = error;
	}

	public static WriteTagResponsePayload success(String uid, String itemId, String isil, String country,
												  int parts, int partNo, int usageType, String afi,
												  String sessionName, String clientId) {
		return new WriteTagResponsePayload(
				"ok", uid, itemId, isil, country, parts, partNo, usageType, afi,
				"Tag written successfully", null, sessionName, System.currentTimeMillis(), clientId
		);
	}

	public static WriteTagResponsePayload error(String uid, String itemId, String errorMessage,
												String sessionName, String clientId) {
		return new WriteTagResponsePayload(
				"error", uid, itemId, null, null, 0, 0, 0, null,
				errorMessage, errorMessage, sessionName, System.currentTimeMillis(), clientId
		);
	}

	public String getStatus() {
		return status;
	}

	public String getUid() {
		return uid;
	}

	public String getItemId() {
		return itemId;
	}

	public String getIsil() {
		return isil;
	}

	public String getCountry() {
		return country;
	}

	public int getParts() {
		return parts;
	}

	public int getPartNo() {
		return partNo;
	}

	public int getUsageType() {
		return usageType;
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
			return String.format("[WRITE TAG] Status: OK | UID: %s | ItemID: %s | ISIL: %s | Country: %s%s",
					uid, itemId, isil, country, (afi != null ? " | AFI: " + afi : ""));
		} else {
			return String.format("[WRITE TAG ERROR] UID: %s | Error: %s", uid, (error != null ? error : message));
		}
	}
}

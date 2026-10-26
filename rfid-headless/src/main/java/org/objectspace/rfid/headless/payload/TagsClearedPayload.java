package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload representing a reader cleared event (all RFID tags removed from reader field).
 */
@JsonIgnoreProperties({"clientId"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagsClearedPayload extends BaseRfidPayload {

	public static final String EVENT_NAME = "rfid:tags_cleared";
	public static final String STATUS_EMPTY = "empty";

	@JsonProperty("status")
	private final String status;

	@JsonProperty("totalTags")
	private final int totalTags;

	public TagsClearedPayload(String sessionName, long timestamp, String clientId) {
		this(sessionName, timestamp, clientId, clientId, STATUS_EMPTY, 0);
	}

	public TagsClearedPayload(String sessionName, long timestamp, String clientId, String readerId) {
		this(sessionName, timestamp, clientId, readerId, STATUS_EMPTY, 0);
	}

	public TagsClearedPayload(String sessionName, long timestamp, String clientId, String status, int totalTags) {
		this(sessionName, timestamp, clientId, clientId, status, totalTags);
	}

	public TagsClearedPayload(String sessionName, long timestamp, String clientId, String readerId, String status, int totalTags) {
		super(EVENT_NAME, sessionName, timestamp, clientId, readerId);
		this.status = status != null ? status : STATUS_EMPTY;
		this.totalTags = totalTags;
	}

	public String getStatus() {
		return status;
	}

	public int getTotalTags() {
		return totalTags;
	}

	@JsonIgnore
	@Override
	public String getSummaryMessage() {
		if (getReaderId() != null && !getReaderId().isBlank()) {
			return "[READER CLEARED] Reader: " + getReaderId() + " | All RFID tags removed from reader field";
		}
		return "[READER CLEARED] All RFID tags removed from reader field";
	}
}

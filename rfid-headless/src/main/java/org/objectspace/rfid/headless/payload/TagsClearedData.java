package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data representing a reader cleared event (all RFID tags removed from reader field).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TagsClearedData(
		@JsonProperty("status") String status,
		@JsonProperty("totalTags") int totalTags,
		@JsonProperty("readerId") String readerId
) {
	public static final String EVENT_NAME = "rfid/tags_cleared";
	public static final String STATUS_EMPTY = "empty";

	public TagsClearedData {
		status = (status != null && !status.isBlank()) ? status : STATUS_EMPTY;
		readerId = (readerId != null && !readerId.isBlank()) ? readerId : null;
	}

	public TagsClearedData(String readerId) {
		this(STATUS_EMPTY, 0, readerId);
	}

	public TagsClearedData() {
		this(STATUS_EMPTY, 0, null);
	}

	@JsonIgnore
	public String getSummaryMessage() {
		if (readerId != null && !readerId.isBlank()) {
			return "[READER CLEARED] Reader: " + readerId + " | All RFID tags removed from reader field";
		}
		return "[READER CLEARED] All RFID tags removed from reader field";
	}
}

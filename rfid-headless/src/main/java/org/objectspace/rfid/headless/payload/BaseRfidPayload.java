package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for all RFID WebSocket message payloads in headless mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseRfidPayload {

	@JsonProperty("event")
	private final String event;

	@JsonProperty("session")
	private final String session;

	@JsonProperty("timestamp")
	private final long timestamp;

	@JsonProperty("clientId")
	private final String clientId;

	@JsonProperty("readerId")
	@JsonAlias({"reader_id", "readerID"})
	private final String readerId;

	protected BaseRfidPayload(String event, String session, long timestamp, String clientId) {
		this(event, session, timestamp, clientId, clientId);
	}

	protected BaseRfidPayload(String event, String session, long timestamp, String clientId, String readerId) {
		this.event = event != null ? event : "";
		this.session = session != null ? session : "";
		this.timestamp = timestamp;
		this.clientId = clientId != null ? clientId : "";
		this.readerId = (readerId != null && !readerId.isBlank()) ? readerId : (clientId != null ? clientId : "");
	}

	public String getEvent() {
		return event;
	}

	public String getSession() {
		return session;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getClientId() {
		return clientId;
	}

	public String getReaderId() {
		return readerId;
	}

	@JsonIgnore
	public abstract String getSummaryMessage();
}

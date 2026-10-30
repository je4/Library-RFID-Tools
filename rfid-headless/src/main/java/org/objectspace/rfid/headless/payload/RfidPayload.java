package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic RFID message payload envelope carrying an event name and strongly-typed data.
 *
 * @param <T> data payload type
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RfidPayload<T>(
		@JsonProperty("event") String event,
		@JsonProperty("data") T data
) {
	public static <T> RfidPayload<T> of(String event, T data) {
		return new RfidPayload<>(event, data);
	}
}

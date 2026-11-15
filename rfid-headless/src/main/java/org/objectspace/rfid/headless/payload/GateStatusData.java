package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.objectspace.rfid.feig.FeigRFID;

import java.util.Collections;
import java.util.List;

/**
 * Data representing gate status and people counter query results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GateStatusData(
		@JsonProperty("readerId") String readerId,
		@JsonProperty("counters") List<FeigRFID.GateCounter> counters,
		@JsonProperty("diagnostics") List<FeigRFID.GateDiagnostic> diagnostics,
		@JsonProperty("status") String status,
		@JsonProperty("time") String time
) {
	public static final String EVENT_NAME = "rfid.gate.status";

	public GateStatusData {
		readerId = (readerId != null && !readerId.isBlank()) ? readerId : null;
		counters = (counters != null) ? List.copyOf(counters) : Collections.emptyList();
		diagnostics = (diagnostics != null) ? List.copyOf(diagnostics) : Collections.emptyList();
		status = (status != null && !status.isBlank()) ? status : "ok";
	}

	public GateStatusData(String readerId, List<FeigRFID.GateCounter> counters, List<FeigRFID.GateDiagnostic> diagnostics) {
		this(readerId, counters, diagnostics, "ok", null);
	}

	public GateStatusData(String readerId, List<FeigRFID.GateCounter> counters, List<FeigRFID.GateDiagnostic> diagnostics, String status) {
		this(readerId, counters, diagnostics, status, null);
	}

	public GateStatusData withTime(String time) {
		return new GateStatusData(readerId, counters, diagnostics, status, time);
	}

	@JsonIgnore
	public String getSummaryMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("[GATE STATUS] Reader: %s | Counters: %d",
				(readerId != null ? readerId : "unknown"),
				counters.size()));
		if (!counters.isEmpty()) {
			sb.append(" [");
			for (int i = 0; i < counters.size(); i++) {
				if (i > 0) sb.append(", ");
				FeigRFID.GateCounter c = counters.get(i);
				sb.append(String.format("c1=%d, c2=%d", c.detector1Counter1(), c.detector1Counter2()));
				if (c.detector2Counter1() > 0 || c.detector2Counter2() > 0) {
					sb.append(String.format(", d2_c1=%d, d2_c2=%d", c.detector2Counter1(), c.detector2Counter2()));
				}
			}
			sb.append("]");
		}
		sb.append(String.format(" | Diagnostics: %d | Status: %s", diagnostics.size(), status));
		return sb.toString();
	}
}

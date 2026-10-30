package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data representing RFID tag AFI write response:
 * - Status ("ok" / "partial" / "error")
 * - UID (primary / single UID)
 * - UIDs (list of all targeted transponder UIDs)
 * - Successful UIDs (list of transponder UIDs where write succeeded)
 * - Failed UIDs (map of transponder UID to error message)
 * - Results (structured per-tag result items)
 * - AFI (written AFI byte formatted as hex string or decimal)
 * - Message / Error description
 * - Reader ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WriteAfiResponseData(
		@JsonProperty("status") String status,
		@JsonProperty("uid") @JsonAlias({"UID"}) String uid,
		@JsonProperty("uids") @JsonAlias({"UIDs"}) List<String> uids,
		@JsonProperty("successfulUids") List<String> successfulUids,
		@JsonProperty("failedUids") Map<String, String> failedUids,
		@JsonProperty("results") List<TagAfiResult> results,
		@JsonProperty("afi") @JsonAlias({"AFI"}) String afi,
		@JsonProperty("message") String message,
		@JsonProperty("error") String error,
		@JsonProperty("readerId") @JsonAlias({"reader_id", "readerID"}) String readerId
) {
	public static final String EVENT_RESPONSE = "rfid:write_afi_response";

	public static WriteAfiResponseData success(String uid, String afi, String readerId) {
		return new WriteAfiResponseData(
				"ok", uid != null ? uid : "",
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of(TagAfiResult.ok(uid)) : Collections.emptyList(),
				afi != null ? afi : "", "AFI written successfully", null, readerId
		);
	}

	public static WriteAfiResponseData error(String uid, String afi, String errorMessage, String readerId) {
		return new WriteAfiResponseData(
				"error", uid != null ? uid : "",
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				Collections.emptyList(),
				(uid != null && !uid.isBlank()) ? Map.of(uid, errorMessage != null ? errorMessage : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of(TagAfiResult.error(uid, errorMessage)) : Collections.emptyList(),
				afi != null ? afi : "", errorMessage, errorMessage, readerId
		);
	}

	public static WriteAfiResponseData batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			String afi,
			String readerId) {
		List<String> reqUids = (requestedUids != null) ? new ArrayList<>(requestedUids) : Collections.emptyList();
		List<String> succUids = (successfulUids != null) ? new ArrayList<>(successfulUids) : Collections.emptyList();
		Map<String, String> failUids = (failedUids != null) ? new LinkedHashMap<>(failedUids) : Collections.emptyMap();

		List<TagAfiResult> resList = new ArrayList<>();
		for (String u : reqUids) {
			if (failUids.containsKey(u)) {
				resList.add(TagAfiResult.error(u, failUids.get(u)));
			} else if (succUids.contains(u)) {
				resList.add(TagAfiResult.ok(u));
			} else {
				resList.add(TagAfiResult.error(u, "Unknown write outcome"));
			}
		}

		String status;
		String message;
		String error = null;
		if (failUids.isEmpty()) {
			status = "ok";
			message = reqUids.size() == 1 ? "AFI written successfully" : String.format("AFI written to %d/%d tags successfully", succUids.size(), reqUids.size());
		} else if (succUids.isEmpty()) {
			status = "error";
			message = reqUids.size() == 1 ? failUids.values().iterator().next() : String.format("Failed to write AFI to all %d tags", reqUids.size());
			error = failUids.values().stream().findFirst().orElse("Failed to write AFI");
		} else {
			status = "partial";
			message = String.format("AFI written to %d/%d tags (%d failed)", succUids.size(), reqUids.size(), failUids.size());
			error = String.format("%d of %d tags failed", failUids.size(), reqUids.size());
		}

		String primaryUid = (reqUids.size() == 1) ? reqUids.get(0) : (!succUids.isEmpty() ? succUids.get(0) : (!reqUids.isEmpty() ? reqUids.get(0) : ""));

		return new WriteAfiResponseData(
				status, primaryUid, reqUids, succUids, failUids, resList, afi != null ? afi : "", message, error, readerId
		);
	}

	@JsonIgnore
	public boolean isSuccess() {
		return "ok".equalsIgnoreCase(status);
	}

	@JsonIgnore
	public boolean isPartial() {
		return "partial".equalsIgnoreCase(status);
	}

	@JsonIgnore
	public String getSummaryMessage() {
		if (isSuccess()) {
			if (uids != null && uids.size() > 1) {
				return String.format("[WRITE AFI] Status: OK | %d tags written | AFI: %s", uids.size(), afi);
			}
			return String.format("[WRITE AFI] Status: OK | UID: %s | AFI: %s", uid, afi);
		} else if ("partial".equalsIgnoreCase(status)) {
			int succCount = (successfulUids != null) ? successfulUids.size() : 0;
			int totalCount = (uids != null) ? uids.size() : 0;
			return String.format("[WRITE AFI PARTIAL] Status: PARTIAL | %d/%d succeeded | AFI: %s", succCount, totalCount, afi);
		} else {
			if (uids != null && uids.size() > 1) {
				return String.format("[WRITE AFI ERROR] %d tags failed | Error: %s", uids.size(), (error != null ? error : message));
			}
			return String.format("[WRITE AFI ERROR] UID: %s | Error: %s", uid, (error != null ? error : message));
		}
	}

	/**
	 * Per-tag AFI write result entry.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class TagAfiResult {
		@JsonProperty("uid")
		private final String uid;

		@JsonProperty("status")
		private final String status;

		@JsonProperty("error")
		private final String error;

		@JsonCreator
		public TagAfiResult(
				@JsonProperty("uid") String uid,
				@JsonProperty("status") String status,
				@JsonProperty("error") String error) {
			this.uid = uid != null ? uid : "";
			this.status = status != null ? status : "ok";
			this.error = error;
		}

		public static TagAfiResult ok(String uid) {
			return new TagAfiResult(uid, "ok", null);
		}

		public static TagAfiResult error(String uid, String error) {
			return new TagAfiResult(uid, "error", error);
		}

		public String getUid() {
			return uid;
		}

		public String getStatus() {
			return status;
		}

		public String getError() {
			return error;
		}

		@JsonIgnore
		public boolean isSuccess() {
			return "ok".equalsIgnoreCase(status);
		}
	}
}

package org.objectspace.rfid.headless.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload representing RFID tag AFI write response:
 * - Status ("ok" / "partial" / "error")
 * - UID (primary / single UID for backward compatibility)
 * - UIDs (list of all targeted transponder UIDs)
 * - Successful UIDs (list of transponder UIDs where write succeeded)
 * - Failed UIDs (map of transponder UID to error message)
 * - Results (structured per-tag result items)
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

	@JsonProperty("uids")
	@JsonAlias({"UIDs"})
	private final List<String> uids;

	@JsonProperty("successfulUids")
	private final List<String> successfulUids;

	@JsonProperty("failedUids")
	private final Map<String, String> failedUids;

	@JsonProperty("results")
	private final List<TagAfiResult> results;

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
		this(status, uid,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? List.of(uid) : Collections.emptyList(),
				!"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? Map.of(uid, error != null ? error : message != null ? message : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of("ok".equalsIgnoreCase(status) ? TagAfiResult.ok(uid) : TagAfiResult.error(uid, error != null ? error : message)) : Collections.emptyList(),
				afi, message, error, sessionName, timestamp, clientId, clientId);
	}

	public WriteAfiResponsePayload(String status, String uid, String afi,
								   String message, String error, String sessionName,
								   long timestamp, String clientId, String readerId) {
		this(status, uid,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? List.of(uid) : Collections.emptyList(),
				!"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? Map.of(uid, error != null ? error : message != null ? message : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of("ok".equalsIgnoreCase(status) ? TagAfiResult.ok(uid) : TagAfiResult.error(uid, error != null ? error : message)) : Collections.emptyList(),
				afi, message, error, sessionName, timestamp, clientId, readerId);
	}

	public WriteAfiResponsePayload(
			String status,
			String uid,
			List<String> uids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			List<TagAfiResult> results,
			String afi,
			String message,
			String error,
			String sessionName,
			long timestamp,
			String clientId) {
		this(status, uid, uids, successfulUids, failedUids, results, afi, message, error, sessionName, timestamp, clientId, clientId);
	}

	@JsonCreator
	public WriteAfiResponsePayload(
			@JsonProperty("status") String status,
			@JsonProperty("uid") String uid,
			@JsonProperty("uids") List<String> uids,
			@JsonProperty("successfulUids") List<String> successfulUids,
			@JsonProperty("failedUids") Map<String, String> failedUids,
			@JsonProperty("results") List<TagAfiResult> results,
			@JsonProperty("afi") String afi,
			@JsonProperty("message") String message,
			@JsonProperty("error") String error,
			@JsonProperty("sessionName") String sessionName,
			@JsonProperty("timestamp") long timestamp,
			@JsonProperty("clientId") String clientId,
			@JsonProperty("readerId") @JsonAlias({"reader_id", "readerID"}) String readerId) {
		super(EVENT_RESPONSE, sessionName, timestamp, clientId, readerId);
		this.status = status != null ? status : "ok";
		this.uid = uid != null ? uid : "";
		this.uids = (uids != null) ? Collections.unmodifiableList(new ArrayList<>(uids)) : null;
		this.successfulUids = (successfulUids != null) ? Collections.unmodifiableList(new ArrayList<>(successfulUids)) : null;
		this.failedUids = (failedUids != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(failedUids)) : null;
		this.results = (results != null) ? Collections.unmodifiableList(new ArrayList<>(results)) : null;
		this.afi = afi != null ? afi : "";
		this.message = message;
		this.error = error;
	}

	public static WriteAfiResponsePayload success(String uid, String afi, String sessionName, String clientId) {
		return success(uid, afi, sessionName, clientId, clientId);
	}

	public static WriteAfiResponsePayload success(String uid, String afi, String sessionName, String clientId, String readerId) {
		return new WriteAfiResponsePayload(
				"ok", uid, afi, "AFI written successfully", null, sessionName,
				System.currentTimeMillis(), clientId, readerId
		);
	}

	public static WriteAfiResponsePayload error(String uid, String afi, String errorMessage,
												String sessionName, String clientId) {
		return error(uid, afi, errorMessage, sessionName, clientId, clientId);
	}

	public static WriteAfiResponsePayload error(String uid, String afi, String errorMessage,
												String sessionName, String clientId, String readerId) {
		return new WriteAfiResponsePayload(
				"error", uid, afi, errorMessage, errorMessage, sessionName,
				System.currentTimeMillis(), clientId, readerId
		);
	}

	public static WriteAfiResponsePayload batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			String afi,
			String sessionName,
			String clientId) {
		return batchResult(requestedUids, successfulUids, failedUids, afi, sessionName, clientId, clientId);
	}

	public static WriteAfiResponsePayload batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			String afi,
			String sessionName,
			String clientId,
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

		return new WriteAfiResponsePayload(
				status, primaryUid, reqUids, succUids, failUids, resList, afi, message, error, sessionName,
				System.currentTimeMillis(), clientId, readerId
		);
	}

	public String getStatus() {
		return status;
	}

	public String getUid() {
		return uid;
	}

	public List<String> getUids() {
		return uids;
	}

	public List<String> getSuccessfulUids() {
		return successfulUids;
	}

	public Map<String, String> getFailedUids() {
		return failedUids;
	}

	public List<TagAfiResult> getResults() {
		return results;
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

	public boolean isPartial() {
		return "partial".equalsIgnoreCase(status);
	}

	@JsonIgnore
	@Override
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

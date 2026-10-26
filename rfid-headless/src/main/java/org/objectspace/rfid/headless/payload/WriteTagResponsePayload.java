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
 * Payload representing RFID tag write response:
 * - Status ("ok" / "partial" / "error")
 * - UID (primary / single UID for backward compatibility)
 * - Item ID (primary item ID for backward compatibility)
 * - ISIL
 * - Country
 * - Parts
 * - Part number
 * - Usage type
 * - AFI (if written)
 * - UIDs (list of all targeted transponder UIDs)
 * - Successful UIDs (list of transponder UIDs where write succeeded)
 * - Failed UIDs (map of transponder UID to error message)
 * - Results (structured per-tag write result items)
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

	@JsonProperty("uids")
	@JsonAlias({"UIDs"})
	private final List<String> uids;

	@JsonProperty("successfulUids")
	private final List<String> successfulUids;

	@JsonProperty("failedUids")
	private final Map<String, String> failedUids;

	@JsonProperty("results")
	private final List<TagWriteResult> results;

	@JsonProperty("message")
	private final String message;

	@JsonProperty("error")
	private final String error;

	public WriteTagResponsePayload(String status, String uid, String itemId, String isil, String country,
								   int parts, int partNo, int usageType, String afi,
								   String message, String error, String sessionName,
								   long timestamp, String clientId) {
		this(status, uid, itemId, isil, country, parts, partNo, usageType, afi,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? List.of(uid) : Collections.emptyList(),
				!"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? Map.of(uid, error != null ? error : message != null ? message : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of("ok".equalsIgnoreCase(status) ? TagWriteResult.ok(uid, itemId, afi) : TagWriteResult.error(uid, itemId, afi, error != null ? error : message)) : Collections.emptyList(),
				message, error, sessionName, timestamp, clientId, clientId);
	}

	public WriteTagResponsePayload(String status, String uid, String itemId, String isil, String country,
								   int parts, int partNo, int usageType, String afi,
								   String message, String error, String sessionName,
								   long timestamp, String clientId, String readerId) {
		this(status, uid, itemId, isil, country, parts, partNo, usageType, afi,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? List.of(uid) : Collections.emptyList(),
				!"ok".equalsIgnoreCase(status) && uid != null && !uid.isBlank() ? Map.of(uid, error != null ? error : message != null ? message : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of("ok".equalsIgnoreCase(status) ? TagWriteResult.ok(uid, itemId, afi) : TagWriteResult.error(uid, itemId, afi, error != null ? error : message)) : Collections.emptyList(),
				message, error, sessionName, timestamp, clientId, readerId);
	}

	public WriteTagResponsePayload(
			String status,
			String uid,
			String itemId,
			String isil,
			String country,
			int parts,
			int partNo,
			int usageType,
			String afi,
			List<String> uids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			List<TagWriteResult> results,
			String message,
			String error,
			String sessionName,
			long timestamp,
			String clientId) {
		this(status, uid, itemId, isil, country, parts, partNo, usageType, afi, uids, successfulUids, failedUids, results, message, error, sessionName, timestamp, clientId, clientId);
	}

	@JsonCreator
	public WriteTagResponsePayload(
			@JsonProperty("status") String status,
			@JsonProperty("uid") String uid,
			@JsonProperty("itemId") String itemId,
			@JsonProperty("isil") String isil,
			@JsonProperty("country") String country,
			@JsonProperty("parts") int parts,
			@JsonProperty("partNo") int partNo,
			@JsonProperty("usageType") int usageType,
			@JsonProperty("afi") String afi,
			@JsonProperty("uids") List<String> uids,
			@JsonProperty("successfulUids") List<String> successfulUids,
			@JsonProperty("failedUids") Map<String, String> failedUids,
			@JsonProperty("results") List<TagWriteResult> results,
			@JsonProperty("message") String message,
			@JsonProperty("error") String error,
			@JsonProperty("sessionName") String sessionName,
			@JsonProperty("timestamp") long timestamp,
			@JsonProperty("clientId") String clientId,
			@JsonProperty("readerId") @JsonAlias({"reader_id", "readerID"}) String readerId) {
		super(EVENT_RESPONSE, sessionName, timestamp, clientId, readerId);
		this.status = status != null ? status : "ok";
		this.uid = uid != null ? uid : "";
		this.itemId = itemId != null ? itemId : "";
		this.isil = isil != null ? isil : "";
		this.country = country != null ? country : "";
		this.parts = parts;
		this.partNo = partNo;
		this.usageType = usageType;
		this.afi = afi;
		this.uids = (uids != null) ? Collections.unmodifiableList(new ArrayList<>(uids)) : null;
		this.successfulUids = (successfulUids != null) ? Collections.unmodifiableList(new ArrayList<>(successfulUids)) : null;
		this.failedUids = (failedUids != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(failedUids)) : null;
		this.results = (results != null) ? Collections.unmodifiableList(new ArrayList<>(results)) : null;
		this.message = message;
		this.error = error;
	}

	public static WriteTagResponsePayload success(String uid, String itemId, String isil, String country,
												  int parts, int partNo, int usageType, String afi,
												  String sessionName, String clientId) {
		return success(uid, itemId, isil, country, parts, partNo, usageType, afi, sessionName, clientId, clientId);
	}

	public static WriteTagResponsePayload success(String uid, String itemId, String isil, String country,
												  int parts, int partNo, int usageType, String afi,
												  String sessionName, String clientId, String readerId) {
		return new WriteTagResponsePayload(
				"ok", uid, itemId, isil, country, parts, partNo, usageType, afi,
				"Tag written successfully", null, sessionName, System.currentTimeMillis(), clientId, readerId
		);
	}

	public static WriteTagResponsePayload error(String uid, String itemId, String errorMessage,
												String sessionName, String clientId) {
		return error(uid, itemId, errorMessage, sessionName, clientId, clientId);
	}

	public static WriteTagResponsePayload error(String uid, String itemId, String errorMessage,
												String sessionName, String clientId, String readerId) {
		return new WriteTagResponsePayload(
				"error", uid, itemId, null, null, 0, 0, 0, null,
				errorMessage, errorMessage, sessionName, System.currentTimeMillis(), clientId, readerId
		);
	}

	public static WriteTagResponsePayload batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			List<TagWriteResult> results,
			String sessionName,
			String clientId) {
		return batchResult(requestedUids, successfulUids, failedUids, results, sessionName, clientId, clientId);
	}

	public static WriteTagResponsePayload batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			List<TagWriteResult> results,
			String sessionName,
			String clientId,
			String readerId) {
		List<String> reqUids = (requestedUids != null) ? requestedUids : Collections.emptyList();
		List<String> succUids = (successfulUids != null) ? successfulUids : Collections.emptyList();
		Map<String, String> failUids = (failedUids != null) ? failedUids : Collections.emptyMap();
		List<TagWriteResult> resList = (results != null) ? results : Collections.emptyList();

		String status;
		String message;
		String error = null;

		if (failUids.isEmpty()) {
			status = "ok";
			message = String.format("All %d tags written successfully", reqUids.size());
		} else if (succUids.isEmpty()) {
			status = "error";
			message = String.format("Failed to write all %d tags", reqUids.size());
			error = !failUids.isEmpty() ? failUids.values().iterator().next() : message;
		} else {
			status = "partial";
			message = String.format("Batch tag write partially succeeded: %d of %d tags written (%d failed)",
					succUids.size(), reqUids.size(), failUids.size());
			error = !failUids.isEmpty() ? failUids.values().iterator().next() : null;
		}

		String primaryUid = !reqUids.isEmpty() ? reqUids.get(0) : "";
		String primaryItemId = !resList.isEmpty() ? resList.get(0).getItemId() : "";
		String primaryAfi = !resList.isEmpty() ? resList.get(0).getAfi() : null;

		return new WriteTagResponsePayload(
				status, primaryUid, primaryItemId, null, null, 0, 0, 0, primaryAfi,
				reqUids, succUids, failUids, resList, message, error, sessionName,
				System.currentTimeMillis(), clientId, readerId
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

	public List<String> getUids() {
		return uids;
	}

	public List<String> getSuccessfulUids() {
		return successfulUids;
	}

	public Map<String, String> getFailedUids() {
		return failedUids;
	}

	public List<TagWriteResult> getResults() {
		return results;
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
				return String.format("[WRITE TAG] Status: OK | %d tags written", uids.size());
			}
			return String.format("[WRITE TAG] Status: OK | UID: %s | ItemID: %s | ISIL: %s | Country: %s%s",
					uid, itemId, isil, country, (afi != null ? " | AFI: " + afi : ""));
		} else if ("partial".equalsIgnoreCase(status)) {
			int succCount = (successfulUids != null) ? successfulUids.size() : 0;
			int totalCount = (uids != null) ? uids.size() : 0;
			return String.format("[WRITE TAG PARTIAL] Status: PARTIAL | %d/%d succeeded", succCount, totalCount);
		} else {
			if (uids != null && uids.size() > 1) {
				return String.format("[WRITE TAG ERROR] %d tags failed | Error: %s", uids.size(), (error != null ? error : message));
			}
			return String.format("[WRITE TAG ERROR] UID: %s | Error: %s", uid, (error != null ? error : message));
		}
	}

	/**
	 * Per-tag write result entry.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class TagWriteResult {
		@JsonProperty("uid")
		private final String uid;

		@JsonProperty("itemId")
		private final String itemId;

		@JsonProperty("status")
		private final String status;

		@JsonProperty("afi")
		private final String afi;

		@JsonProperty("error")
		private final String error;

		@JsonCreator
		public TagWriteResult(
				@JsonProperty("uid") String uid,
				@JsonProperty("itemId") String itemId,
				@JsonProperty("status") String status,
				@JsonProperty("afi") String afi,
				@JsonProperty("error") String error) {
			this.uid = uid != null ? uid : "";
			this.itemId = itemId != null ? itemId : "";
			this.status = status != null ? status : "ok";
			this.afi = afi;
			this.error = error;
		}

		public static TagWriteResult ok(String uid, String itemId, String afi) {
			return new TagWriteResult(uid, itemId, "ok", afi, null);
		}

		public static TagWriteResult error(String uid, String itemId, String error) {
			return new TagWriteResult(uid, itemId, "error", null, error);
		}

		public static TagWriteResult error(String uid, String itemId, String afi, String error) {
			return new TagWriteResult(uid, itemId, "error", afi, error);
		}

		public String getUid() {
			return uid;
		}

		public String getItemId() {
			return itemId;
		}

		public String getStatus() {
			return status;
		}

		public String getAfi() {
			return afi;
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

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
 * Data representing RFID tag write response:
 * - Status ("ok" / "partial" / "error")
 * - UID (primary / single UID)
 * - Item ID (primary item ID)
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
 * - Reader ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WriteTagResponseData(
		@JsonProperty("status") String status,
		@JsonProperty("uid") @JsonAlias({"UID"}) String uid,
		@JsonProperty("itemId") @JsonAlias({"imtemId", "primaryItemId", "item_id", "barcode"}) String itemId,
		@JsonProperty("isil") @JsonAlias({"ISIL"}) String isil,
		@JsonProperty("country") @JsonAlias({"countryOfOwnerLib"}) String country,
		@JsonProperty("parts") int parts,
		@JsonProperty("partNo") @JsonAlias({"partno", "partNumber"}) int partNo,
		@JsonProperty("usageType") @JsonAlias({"typeOfUsage"}) int usageType,
		@JsonProperty("afi") @JsonAlias({"AFI"}) String afi,
		@JsonProperty("uids") @JsonAlias({"UIDs"}) List<String> uids,
		@JsonProperty("successfulUids") List<String> successfulUids,
		@JsonProperty("failedUids") Map<String, String> failedUids,
		@JsonProperty("results") List<TagWriteResult> results,
		@JsonProperty("message") String message,
		@JsonProperty("error") String error,
		@JsonProperty("readerId") @JsonAlias({"reader_id", "readerID"}) String readerId
) {
	public static final String EVENT_RESPONSE = "rfid:write_tag_response";

	public static WriteTagResponseData success(String uid, String itemId, String isil, String country,
											   int parts, int partNo, int usageType, String afi, String readerId) {
		return new WriteTagResponseData(
				"ok", uid != null ? uid : "", itemId != null ? itemId : "", isil != null ? isil : "", country != null ? country : "",
				parts, partNo, usageType, afi,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of(TagWriteResult.ok(uid, itemId, afi)) : Collections.emptyList(),
				"Tag written successfully", null, readerId
		);
	}

	public static WriteTagResponseData error(String uid, String itemId, String errorMessage, String readerId) {
		return new WriteTagResponseData(
				"error", uid != null ? uid : "", itemId != null ? itemId : "", "", "", 0, 0, 0, null,
				(uid != null && !uid.isBlank()) ? List.of(uid) : Collections.emptyList(),
				Collections.emptyList(),
				(uid != null && !uid.isBlank()) ? Map.of(uid, errorMessage != null ? errorMessage : "Error") : Collections.emptyMap(),
				(uid != null && !uid.isBlank()) ? List.of(TagWriteResult.error(uid, itemId, errorMessage)) : Collections.emptyList(),
				errorMessage, errorMessage, readerId
		);
	}

	public static WriteTagResponseData batchResult(
			List<String> requestedUids,
			List<String> successfulUids,
			Map<String, String> failedUids,
			List<TagWriteResult> results,
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

		return new WriteTagResponseData(
				status, primaryUid, primaryItemId, "", "", 0, 0, 0, primaryAfi,
				reqUids, succUids, failUids, resList, message, error, readerId
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
	@JsonIgnoreProperties(ignoreUnknown = true)
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

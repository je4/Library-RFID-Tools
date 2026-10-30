package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.objectspace.rfid.FinnishDataModel;

/**
 * Data representing a scanned RFID transponder tag read event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TagReadData(
		@JsonProperty("uid") String uid,
		@JsonProperty("itemId") String itemId,
		@JsonProperty("isil") String isil,
		@JsonProperty("country") String country,
		@JsonProperty("crcOk") boolean crcOk,
		@JsonProperty("isCrcValid") boolean isCrcValid,
		@JsonProperty("crc") String crc,
		@JsonProperty("tagName") String tagName,
		@JsonProperty("raw") String raw,
		@JsonProperty("parts") Integer parts,
		@JsonProperty("partNo") Integer partNo,
		@JsonProperty("usageType") Integer usageType,
		@JsonProperty("version") Integer version,
		@JsonProperty("afi") String afi,
		@JsonProperty("readerId") String readerId
) {
	public static final String EVENT_NAME = "rfid:tag_read";

	public TagReadData(String uid, String itemId, String rawHex, String isil, String country,
					   boolean crcOk, String tagName, FinnishDataModel metadata, String afi, String readerId) {
		this(
				uid != null ? uid : "",
				itemId != null ? itemId : "empty",
				isil != null ? isil : "",
				country != null ? country : "",
				crcOk,
				crcOk,
				crcOk ? "OK" : "ERROR",
				tagName != null ? tagName : "",
				rawHex != null ? rawHex : "",
				(metadata != null && !metadata.isEmpty()) ? metadata.getPartsInItem() : null,
				(metadata != null && !metadata.isEmpty()) ? metadata.getPartNumber() : null,
				(metadata != null && !metadata.isEmpty()) ? metadata.getTypeOfUsage() : null,
				(metadata != null && !metadata.isEmpty()) ? metadata.getVersion() : null,
				(afi != null && !afi.trim().isEmpty()) ? afi.trim() : null,
				(readerId != null && !readerId.isBlank()) ? readerId : null
		);
	}

	public TagReadData(String uid, String itemId, String rawHex, String isil, String country,
					   boolean crcOk, String tagName, Integer parts, Integer partNo,
					   Integer usageType, Integer version, String afi, String readerId) {
		this(
				uid != null ? uid : "",
				itemId != null ? itemId : "empty",
				isil != null ? isil : "",
				country != null ? country : "",
				crcOk,
				crcOk,
				crcOk ? "OK" : "ERROR",
				tagName != null ? tagName : "",
				rawHex != null ? rawHex : "",
				parts,
				partNo,
				usageType,
				version,
				(afi != null && !afi.trim().isEmpty()) ? afi.trim() : null,
				(readerId != null && !readerId.isBlank()) ? readerId : null
		);
	}

	@JsonIgnore
	public String getSummaryMessage() {
		String base = String.format("[TAG READ] UID: %s | ItemID: %s | ISIL: %s | Country: %s | CRC: %s",
				uid, itemId, isil, country, (crcOk ? "OK" : "ERROR"));
		if (afi != null) {
			base += " | AFI: " + afi;
		}
		if (readerId != null && !readerId.isBlank()) {
			base += " | Reader: " + readerId;
		}
		return base;
	}
}

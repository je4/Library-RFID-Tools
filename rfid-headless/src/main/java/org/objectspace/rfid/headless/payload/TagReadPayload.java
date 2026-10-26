package org.objectspace.rfid.headless.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.objectspace.rfid.FinnishDataModel;

/**
 * Payload representing an RFID tag read event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagReadPayload extends BaseRfidPayload {

	public static final String EVENT_NAME = "rfid:tag_read";

	@JsonProperty("uid")
	private final String uid;

	@JsonProperty("itemId")
	private final String itemId;

	@JsonProperty("isil")
	private final String isil;

	@JsonProperty("country")
	private final String country;

	@JsonProperty("crcOk")
	private final boolean crcOk;

	@JsonProperty("isCrcValid")
	private final boolean isCrcValid;

	@JsonProperty("crc")
	private final String crc;

	@JsonProperty("tagName")
	private final String tagName;

	@JsonProperty("raw")
	private final String raw;

	@JsonProperty("parts")
	private Integer parts;

	@JsonProperty("partNo")
	private Integer partNo;

	@JsonProperty("usageType")
	private Integer usageType;

	@JsonProperty("version")
	private Integer version;

	@JsonProperty("afi")
	private final String afi;

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, FinnishDataModel metadata) {
		this(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, clientId, metadata, null);
	}

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, FinnishDataModel metadata, String afi) {
		this(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, clientId, metadata, afi);
	}

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, String readerId, FinnishDataModel metadata, String afi) {
		super(EVENT_NAME, sessionName, timestamp, clientId, readerId);
		this.uid = uid != null ? uid : "";
		this.itemId = itemId != null ? itemId : "empty";
		this.isil = isil != null ? isil : "";
		this.country = country != null ? country : "";
		this.crcOk = crcOk;
		this.isCrcValid = crcOk;
		this.crc = crcOk ? "OK" : "ERROR";
		this.tagName = tagName != null ? tagName : "";
		this.raw = rawHex != null ? rawHex : "";
		this.afi = (afi != null && !afi.trim().isEmpty()) ? afi.trim() : null;

		if (metadata != null && !metadata.isEmpty()) {
			this.parts = metadata.getPartsInItem();
			this.partNo = metadata.getPartNumber();
			this.usageType = metadata.getTypeOfUsage();
			this.version = metadata.getVersion();
		}
	}

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, Integer parts, Integer partNo, Integer usageType, Integer version) {
		this(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, clientId, parts, partNo, usageType, version, null);
	}

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, Integer parts, Integer partNo, Integer usageType, Integer version, String afi) {
		this(uid, itemId, rawHex, isil, country, crcOk, tagName, sessionName, timestamp, clientId, clientId, parts, partNo, usageType, version, afi);
	}

	public TagReadPayload(String uid, String itemId, String rawHex, String isil, String country,
						  boolean crcOk, String tagName, String sessionName, long timestamp,
						  String clientId, String readerId, Integer parts, Integer partNo, Integer usageType, Integer version, String afi) {
		super(EVENT_NAME, sessionName, timestamp, clientId, readerId);
		this.uid = uid != null ? uid : "";
		this.itemId = itemId != null ? itemId : "empty";
		this.isil = isil != null ? isil : "";
		this.country = country != null ? country : "";
		this.crcOk = crcOk;
		this.isCrcValid = crcOk;
		this.crc = crcOk ? "OK" : "ERROR";
		this.tagName = tagName != null ? tagName : "";
		this.raw = rawHex != null ? rawHex : "";
		this.parts = parts;
		this.partNo = partNo;
		this.usageType = usageType;
		this.version = version;
		this.afi = (afi != null && !afi.trim().isEmpty()) ? afi.trim() : null;
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

	public boolean isCrcOk() {
		return crcOk;
	}

	public boolean isCrcValid() {
		return isCrcValid;
	}

	public String getCrc() {
		return crc;
	}

	public String getTagName() {
		return tagName;
	}

	public String getRaw() {
		return raw;
	}

	public Integer getParts() {
		return parts;
	}

	public Integer getPartNo() {
		return partNo;
	}

	public Integer getUsageType() {
		return usageType;
	}

	public Integer getVersion() {
		return version;
	}

	public String getAfi() {
		return afi;
	}

	@JsonIgnore
	@Override
	public String getSummaryMessage() {
		String base = String.format("[TAG READ] UID: %s | ItemID: %s | ISIL: %s | Country: %s | CRC: %s",
				uid, itemId, isil, country, (crcOk ? "OK" : "ERROR"));
		if (afi != null) {
			base += " | AFI: " + afi;
		}
		if (getReaderId() != null && !getReaderId().isBlank()) {
			base += " | Reader: " + getReaderId();
		}
		return base;
	}
}

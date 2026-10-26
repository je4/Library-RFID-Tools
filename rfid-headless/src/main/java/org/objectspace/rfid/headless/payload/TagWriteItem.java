package org.objectspace.rfid.headless.payload;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data structure representing a single RFID tag write specification in batch or single tag write operations:
 * - UID (target transponder UID or wildcard / empty)
 * - Item ID (primary item identifier / barcode)
 * - ISIL (library identifier)
 * - Country (country code)
 * - Parts (total parts count)
 * - Part number (part index)
 * - Usage type (type of usage)
 * - AFI (optional AFI byte / hex string / int)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagWriteItem {

	@JsonProperty("uid")
	@JsonAlias({"UID"})
	private final String uid;

	@JsonProperty("itemId")
	@JsonAlias({"imtemId", "primaryItemId", "item_id", "barcode", "item", "data"})
	private final String itemId;

	@JsonProperty("isil")
	@JsonAlias({"ISIL"})
	private final String isil;

	@JsonProperty("country")
	@JsonAlias({"countryOfOwnerLib"})
	private final String country;

	@JsonProperty("parts")
	@JsonAlias({"partsInItem"})
	private final int parts;

	@JsonProperty("partNo")
	@JsonAlias({"partno", "partNumber"})
	private final int partNo;

	@JsonProperty("usageType")
	@JsonAlias({"typeOfUsage"})
	private final int usageType;

	@JsonProperty("afi")
	@JsonAlias({"AFI"})
	private final Object afi;

	@JsonCreator
	public TagWriteItem(
			@JsonProperty("uid") String uid,
			@JsonProperty("itemId") String itemId,
			@JsonProperty("isil") String isil,
			@JsonProperty("country") String country,
			@JsonProperty("parts") Integer parts,
			@JsonProperty("partNo") Integer partNo,
			@JsonProperty("usageType") Integer usageType,
			@JsonProperty("afi") Object afi) {
		this.uid = uid != null ? uid.trim() : "";
		this.itemId = itemId != null ? itemId : "";
		this.isil = isil != null ? isil : "";
		this.country = country != null ? country : "";
		this.parts = (parts != null && parts > 0) ? parts : 1;
		this.partNo = (partNo != null && partNo > 0) ? partNo : 1;
		this.usageType = (usageType != null && usageType > 0) ? usageType : 1;
		this.afi = afi;
	}

	public TagWriteItem(String uid, String itemId, String isil, String country,
						int parts, int partNo, int usageType, Object afi) {
		this(uid, itemId, isil, country, Integer.valueOf(parts), Integer.valueOf(partNo), Integer.valueOf(usageType), afi);
	}

	public TagWriteItem(String uid, String itemId, String isil, String country, Object afi) {
		this(uid, itemId, isil, country, 1, 1, 1, afi);
	}

	public TagWriteItem(String uid, String itemId, Object afi) {
		this(uid, itemId, "", "", 1, 1, 1, afi);
	}

	public TagWriteItem(String uid, String itemId) {
		this(uid, itemId, "", "", 1, 1, 1, null);
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

	public Object getAfi() {
		return afi;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TagWriteItem that = (TagWriteItem) o;
		return parts == that.parts &&
				partNo == that.partNo &&
				usageType == that.usageType &&
				Objects.equals(uid, that.uid) &&
				Objects.equals(itemId, that.itemId) &&
				Objects.equals(isil, that.isil) &&
				Objects.equals(country, that.country) &&
				Objects.equals(afi, that.afi);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uid, itemId, isil, country, parts, partNo, usageType, afi);
	}

	@Override
	public String toString() {
		return "TagWriteItem{" +
				"uid='" + uid + '\'' +
				", itemId='" + itemId + '\'' +
				", isil='" + isil + '\'' +
				", country='" + country + '\'' +
				", parts=" + parts +
				", partNo=" + partNo +
				", usageType=" + usageType +
				", afi=" + afi +
				'}';
	}
}

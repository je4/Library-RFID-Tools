package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Stateless parser and helper utilities for WebSocket broker messages,
 * tag write payloads, and AFI parameters.
 */
public final class WsBrokerPayloadParser {

	private WsBrokerPayloadParser() {
	}

	/**
	 * Flexible parser for AFI byte parameters (supports Integer, hex string e.g. '07' or '0x07', decimal string).
	 *
	 * @param afiObj AFI parameter object or null
	 * @return parsed Byte or null if missing/empty
	 */
	public static Byte parseAfi(Object afiObj) {
		if (afiObj == null) {
			return null;
		}
		if (afiObj instanceof Number) {
			return ((Number) afiObj).byteValue();
		}
		String s = afiObj.toString().trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.startsWith("0x") || s.startsWith("0X")) {
			return (byte) Integer.parseInt(s.substring(2), 16);
		}
		try {
			return (byte) Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return (byte) Integer.parseInt(s, 16);
		}
	}

	/**
	 * Formats an AFI byte into a 2-digit uppercase hex string (e.g. '07', 'C2').
	 *
	 * @param afi AFI byte value
	 * @return hex string or null
	 */
	public static String formatAfiHex(Byte afi) {
		if (afi == null) {
			return null;
		}
		return String.format("%02X", afi.byteValue() & 0xFF);
	}

	/**
	 * Extracts command / event name from payload data or event message.
	 */
	public static String extractEventName(Event<?> evt, JsonNode dataNode) {
		String eventName = null;
		if (dataNode != null && dataNode.isObject()) {
			if (dataNode.hasNonNull("event")) {
				eventName = dataNode.get("event").asText();
			}
		} else if (dataNode != null && dataNode.isTextual()) {
			eventName = dataNode.asText();
		}

		if ((eventName == null || eventName.isBlank()) && evt != null && evt.getMessage() != null) {
			eventName = evt.getMessage();
		}
		return eventName;
	}

	/**
	 * Checks if a message event is addressed to the client, one of its channels, or registered readers.
	 */
	public static boolean isAddressedToUs(Event<?> evt, String clientName, List<String> channels, Map<String, HeadlessReaderInstance> readerInstances) {
		if (evt == null) {
			return false;
		}
		String target = evt.getTarget();
		if (target == null || target.isBlank()) {
			return true;
		}
		if (clientName != null && target.equalsIgnoreCase(clientName)) {
			return true;
		}
		if (channels != null && channels.contains(target)) {
			return true;
		}
		return readerInstances != null && readerInstances.containsKey(target.toLowerCase());
	}

	/**
	 * Extracts requested reader ID from message payload data or event target.
	 */
	public static String extractRequestedReaderId(JsonNode dataNode, Event<?> evt, String clientName, List<String> channels) {
		if (dataNode != null && dataNode.isObject()) {
			String[] readerKeys = {"reader_id", "readerId", "readerID", "reader", "target_reader", "targetReader"};
			for (String k : readerKeys) {
				if (dataNode.hasNonNull(k) && !dataNode.get(k).asText().isBlank()) {
					return dataNode.get(k).asText().trim();
				}
			}
		}
		if (evt != null && evt.getTarget() != null && !evt.getTarget().isBlank()) {
			String t = evt.getTarget().trim();
			if ((clientName == null || !t.equalsIgnoreCase(clientName)) && (channels == null || !channels.contains(t))) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Parses a collection of TagWriteItem specifications from flexible JSON representations.
	 */
	public static List<TagWriteItem> parseTagWriteItems(JsonNode data) {
		List<TagWriteItem> items = new ArrayList<>();
		if (data == null || data.isNull()) {
			return items;
		}

		if (data.isArray()) {
			int index = 0;
			for (JsonNode child : data) {
				TagWriteItem item = parseSingleTagWriteItem(child, null, null, index + 1);
				if (item != null) {
					items.add(item);
				}
				index++;
			}
			return items;
		}

		if (data.isObject()) {
			// 1. Check array or map wrapper keys
			String[] arrayKeys = {"tags", "items", "transponders", "elements", "list", "data"};
			for (String key : arrayKeys) {
				if (data.hasNonNull(key)) {
					JsonNode node = data.get(key);
					if (node.isArray()) {
						int index = 0;
						for (JsonNode child : node) {
							TagWriteItem item = parseSingleTagWriteItem(child, data, null, index + 1);
							if (item != null) {
								items.add(item);
							}
							index++;
						}
						if (!items.isEmpty()) {
							return items;
						}
					} else if (node.isObject()) {
						boolean isMap = false;
						Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
						int index = 0;
						while (fields.hasNext()) {
							Map.Entry<String, JsonNode> entry = fields.next();
							if (entry.getValue().isObject()) {
								TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, entry.getKey(), index + 1);
								if (item != null) {
									items.add(item);
									isMap = true;
								}
							} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
								items.add(new TagWriteItem(
										entry.getKey(),
										entry.getValue().asText(),
										extractString(data, null, "isil", "ISIL"),
										extractString(data, null, "country", "countryOfOwnerLib"),
										extractInt(data, null, 1, "parts", "partsInItem"),
										extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
										extractInt(data, null, 1, "usageType", "typeOfUsage"),
										extractAfi(data, null)));
								isMap = true;
							}
							index++;
						}
						if (isMap && !items.isEmpty()) {
							return items;
						}
					}
				}
			}

			// 2. Check if uids / UIDs is present (as array or map)
			if ((data.hasNonNull("uids") && (data.get("uids").isArray() || data.get("uids").isObject())) ||
				(data.hasNonNull("UIDs") && (data.get("UIDs").isArray() || data.get("UIDs").isObject()))) {
				JsonNode uidsNode = data.hasNonNull("uids") ? data.get("uids") : data.get("UIDs");
				if (uidsNode.isArray()) {
					JsonNode itemIdsNode = null;
					String[] itemArrayKeys = {"itemIds", "item_ids", "barcodes", "items"};
					for (String k : itemArrayKeys) {
						if (data.hasNonNull(k) && data.get(k).isArray()) {
							itemIdsNode = data.get(k);
							break;
						}
					}
					JsonNode afisNode = null;
					String[] afiArrayKeys = {"afis", "AFIs", "afiList"};
					for (String k : afiArrayKeys) {
						if (data.hasNonNull(k) && data.get(k).isArray()) {
							afisNode = data.get(k);
							break;
						}
					}

					int index = 0;
					int totalCount = uidsNode.size();
					for (JsonNode child : uidsNode) {
						if (child.isTextual() && !child.asText().isBlank()) {
							String uid = child.asText().trim();
							String itemId = (itemIdsNode != null && itemIdsNode.size() > index)
									? itemIdsNode.get(index).asText()
									: extractString(data, null, "itemId", "imtemId", "primaryItemId", "item_id", "barcode", "item");
							Object afi = (afisNode != null && afisNode.size() > index)
									? (afisNode.get(index).isInt() ? afisNode.get(index).asInt() : afisNode.get(index).asText())
									: extractAfi(data, null);
							String isil = extractString(data, null, "isil", "ISIL");
							String country = extractString(data, null, "country", "countryOfOwnerLib");
							int parts = extractInt(data, null, totalCount > 1 ? totalCount : 1, "parts", "partsInItem");
							int partNo = extractInt(data, null, index + 1, "partno", "partNo", "partNumber");
							int usageType = extractInt(data, null, 1, "usageType", "typeOfUsage");
							items.add(new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afi));
						} else if (child.isObject()) {
							TagWriteItem item = parseSingleTagWriteItem(child, data, null, index + 1);
							if (item != null) {
								items.add(item);
							}
						}
						index++;
					}
					if (!items.isEmpty()) {
						return items;
					}
				} else if (uidsNode.isObject()) {
					int index = 0;
					Iterator<Map.Entry<String, JsonNode>> fields = uidsNode.fields();
					while (fields.hasNext()) {
						Map.Entry<String, JsonNode> entry = fields.next();
						if (entry.getValue().isObject()) {
							TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, entry.getKey(), index + 1);
							if (item != null) {
								items.add(item);
							}
						} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
							items.add(new TagWriteItem(
									entry.getKey(),
									entry.getValue().asText(),
									extractString(data, null, "isil", "ISIL"),
									extractString(data, null, "country", "countryOfOwnerLib"),
									extractInt(data, null, 1, "parts", "partsInItem"),
									extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
									extractInt(data, null, 1, "usageType", "typeOfUsage"),
									extractAfi(data, null)));
						}
						index++;
					}
					if (!items.isEmpty()) {
						return items;
					}
				}
			}

			// 3. Check single item representation at root level
			TagWriteItem single = parseSingleTagWriteItem(data, null, null, 1);
			if (single != null && (!single.getUid().isEmpty() || !single.getItemId().isEmpty() || single.getAfi() != null)) {
				items.add(single);
				return items;
			}

			// 4. Check root-level map (e.g. { "E004...": { "itemId": "123" } } or { "E004...": "123" })
			Iterator<Map.Entry<String, JsonNode>> rootFields = data.fields();
			boolean isRootMap = false;
			int index = 0;
			while (rootFields.hasNext()) {
				Map.Entry<String, JsonNode> entry = rootFields.next();
				String k = entry.getKey();
				if (k.startsWith("E004") || k.startsWith("e004") || k.length() == 16) {
					if (entry.getValue().isObject()) {
						TagWriteItem item = parseSingleTagWriteItem(entry.getValue(), data, k, index + 1);
						if (item != null) {
							items.add(item);
							isRootMap = true;
						}
					} else if (entry.getValue().isTextual() || entry.getValue().isNumber()) {
						items.add(new TagWriteItem(
								k,
								entry.getValue().asText(),
								extractString(data, null, "isil", "ISIL"),
								extractString(data, null, "country", "countryOfOwnerLib"),
								extractInt(data, null, 1, "parts", "partsInItem"),
								extractInt(data, null, index + 1, "partno", "partNo", "partNumber"),
								extractInt(data, null, 1, "usageType", "typeOfUsage"),
								extractAfi(data, null)));
						isRootMap = true;
					}
					index++;
				}
			}
			if (isRootMap && !items.isEmpty()) {
				return items;
			}
		}

		return items;
	}

	public static TagWriteItem parseSingleTagWriteItem(JsonNode node, JsonNode parentNode, String defaultUid, int defaultPartNo) {
		if (node == null || node.isNull()) {
			return null;
		}

		if (node.isTextual()) {
			String u = node.asText().trim();
			return new TagWriteItem(u.isEmpty() ? (defaultUid != null ? defaultUid : "") : u,
					extractString(parentNode, null, "itemId", "imtemId", "primaryItemId", "item_id", "barcode", "item"),
					extractString(parentNode, null, "isil", "ISIL"),
					extractString(parentNode, null, "country", "countryOfOwnerLib"),
					extractInt(parentNode, null, 1, "parts", "partsInItem"),
					extractInt(parentNode, null, defaultPartNo, "partno", "partNo", "partNumber"),
					extractInt(parentNode, null, 1, "usageType", "typeOfUsage"),
					extractAfi(parentNode, null));
		}

		String uid = extractString(node, parentNode, "uid", "UID", "tag_id", "tagId", "tagID", "id");
		if (uid.isEmpty() && defaultUid != null) {
			uid = defaultUid;
		}

		String itemId = extractString(node, parentNode, "itemId", "imtemId", "primaryItemId", "item_id", "barcode", "item", "title", "id");
		String isil = extractString(node, parentNode, "isil", "ISIL");
		String country = extractString(node, parentNode, "country", "countryOfOwnerLib");
		int parts = extractInt(node, parentNode, 1, "parts", "partsInItem", "totalParts");
		int partNo = extractInt(node, parentNode, defaultPartNo, "partno", "partNo", "partNumber", "part");
		int usageType = extractInt(node, parentNode, 1, "usageType", "typeOfUsage");
		Object afi = extractAfi(node, parentNode);

		return new TagWriteItem(uid, itemId, isil, country, parts, partNo, usageType, afi);
	}

	public static String extractString(JsonNode primary, JsonNode secondary, String... keys) {
		for (String k : keys) {
			if (primary != null && primary.hasNonNull(k)) {
				return primary.get(k).asText().trim();
			}
		}
		if (secondary != null) {
			for (String k : keys) {
				if (secondary.hasNonNull(k)) {
					return secondary.get(k).asText().trim();
				}
			}
		}
		return "";
	}

	public static int extractInt(JsonNode primary, JsonNode secondary, int defaultValue, String... keys) {
		for (String k : keys) {
			if (primary != null && primary.hasNonNull(k)) {
				JsonNode n = primary.get(k);
				if (n.isInt()) return n.asInt();
				try {
					return Integer.parseInt(n.asText().trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		if (secondary != null) {
			for (String k : keys) {
				if (secondary.hasNonNull(k)) {
					JsonNode n = secondary.get(k);
					if (n.isInt()) return n.asInt();
					try {
						return Integer.parseInt(n.asText().trim());
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return defaultValue;
	}

	public static Object extractAfi(JsonNode primary, JsonNode secondary) {
		String[] keys = {"afi", "AFI", "afiByte", "afi_byte", "afiHex", "afi_hex"};
		for (String k : keys) {
			if (primary != null && primary.hasNonNull(k)) {
				JsonNode n = primary.get(k);
				return n.isInt() ? n.asInt() : n.asText();
			}
		}
		if (secondary != null) {
			for (String k : keys) {
				if (secondary.hasNonNull(k)) {
					JsonNode n = secondary.get(k);
					return n.isInt() ? n.asInt() : n.asText();
				}
			}
		}
		return null;
	}

	/**
	 * Extracts target UIDs list for write_afi commands.
	 */
	public static List<String> extractTargetUidsForAfi(JsonNode data) {
		List<String> targetUids = new ArrayList<>();
		if (data != null && data.isObject()) {
			if (data.hasNonNull("uids") && data.get("uids").isArray()) {
				for (JsonNode uNode : data.get("uids")) {
					if (uNode.isTextual() && !uNode.asText().isBlank()) {
						targetUids.add(uNode.asText().trim());
					}
				}
			} else if (data.hasNonNull("UIDs") && data.get("UIDs").isArray()) {
				for (JsonNode uNode : data.get("UIDs")) {
					if (uNode.isTextual() && !uNode.asText().isBlank()) {
						targetUids.add(uNode.asText().trim());
					}
				}
			} else if (data.hasNonNull("uid")) {
				String u = data.get("uid").asText();
				if (u != null && !u.isBlank()) targetUids.add(u.trim());
			} else if (data.hasNonNull("UID")) {
				String u = data.get("UID").asText();
				if (u != null && !u.isBlank()) targetUids.add(u.trim());
			}
		}
		return targetUids;
	}

	/**
	 * Extracts AFI object (Integer or String) for write_afi commands.
	 */
	public static Object extractAfiObject(JsonNode data) {
		if (data != null && data.isObject()) {
			if (data.hasNonNull("afi")) {
				JsonNode afiNode = data.get("afi");
				return afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			} else if (data.hasNonNull("AFI")) {
				JsonNode afiNode = data.get("AFI");
				return afiNode.isInt() ? afiNode.asInt() : afiNode.asText();
			}
		}
		return null;
	}
}

package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.headless.payload.DeviceMetadataData;
import org.objectspace.rfid.headless.payload.TagReadData;
import org.objectspace.rfid.headless.payload.TagsClearedData;
import org.objectspace.wsbrokerclient.event.Event;

import static org.junit.jupiter.api.Assertions.*;

public class WsBrokerPayloadModelTest {

    private static JsonNode payloadData(Event<?> evt) {
        return evt.getDataNode();
    }

    @Test
    @DisplayName("Test TagReadData and TagsClearedData model properties and summary messages")
    void testPayloadModelAndSummary() {
        TagReadData tagData = new TagReadData(
                "UID-1", "ITEM-1", "HEX1", "ISIL1", "DE",
                true, "TagType", (FinnishDataModel) null, null, "Client1"
        );

        assertEquals("UID-1", tagData.uid());
        assertEquals("ITEM-1", tagData.itemId());
        assertEquals("HEX1", tagData.raw());
        assertEquals("ISIL1", tagData.isil());
        assertEquals("DE", tagData.country());
        assertTrue(tagData.crcOk());
        assertTrue(tagData.isCrcValid());
        assertEquals("OK", tagData.crc());
        assertEquals("TagType", tagData.tagName());
        assertEquals("Client1", tagData.readerId());
        assertNull(tagData.parts());
        assertNull(tagData.afi());
        assertTrue(tagData.getSummaryMessage().contains("UID: UID-1"));
        assertFalse(tagData.getSummaryMessage().contains("AFI:"));

        Event eNoAfi = Event.of(TagReadData.EVENT_NAME, "Client1", "ch1", tagData);
        assertFalse(payloadData(eNoAfi).has("afi"), "Null AFI should be omitted from JSON");

        // With AFI
        TagReadData tagDataWithAfi = new TagReadData(
                "UID-1", "ITEM-1", "HEX1", "ISIL1", "DE",
                true, "TagType", (FinnishDataModel) null, "07", "Client1"
        );
        assertEquals("07", tagDataWithAfi.afi());
        assertTrue(tagDataWithAfi.getSummaryMessage().contains("AFI: 07"));

        Event eWithAfi = Event.of(TagReadData.EVENT_NAME, "Client1", "ch1", tagDataWithAfi);
        assertTrue(payloadData(eWithAfi).has("afi"), "AFI should be present in JSON when set");
        assertEquals("07", payloadData(eWithAfi).get("afi").asText());

        TagsClearedData clearedData = new TagsClearedData("Client1");
        assertEquals("empty", clearedData.status());
        assertEquals(0, clearedData.totalTags());
        assertEquals("Client1", clearedData.readerId());
        assertTrue(clearedData.getSummaryMessage().contains("[READER CLEARED]"));

        // With explicit readerId
        TagReadData tagDataWithReader = new TagReadData(
                "UID-2", "ITEM-2", "HEX2", "ISIL2", "CH",
                true, "TagType", null, "07", "gate-01"
        );
        assertEquals("gate-01", tagDataWithReader.readerId());
        assertTrue(tagDataWithReader.getSummaryMessage().contains("Reader: gate-01"));
        assertTrue(tagDataWithReader.getSummaryMessage().contains("AFI: 07"));

        TagsClearedData clearedDataWithReader = new TagsClearedData("gate-01");
        assertEquals("gate-01", clearedDataWithReader.readerId());
        assertTrue(clearedDataWithReader.getSummaryMessage().contains("Reader: gate-01"));
    }

    @Test
    @DisplayName("Test DeviceMetadataData model properties and summary message")
    void testDeviceMetadataPayloadModelAndSummary() {
        DeviceMetadataData data = new DeviceMetadataData(
                "0052E688",
                "ISC.MR102-USB",
                "FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00",
                "reader-01"
        );

        assertEquals("ok", data.status());
        assertEquals("0052E688", data.deviceId());
        assertEquals("ISC.MR102-USB", data.deviceType());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", data.initDescription());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", data.getDescription());
        assertEquals("reader-01", data.readerId());

        String summary = data.getSummaryMessage();
        assertTrue(summary.contains("[DEVICE METADATA]"));
        assertTrue(summary.contains("0052E688"));
        assertTrue(summary.contains("ISC.MR102-USB"));
    }
}

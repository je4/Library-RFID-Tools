package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.headless.payload.DeviceMetadataData;
import org.objectspace.rfid.headless.payload.GateStatusData;
import org.objectspace.rfid.headless.payload.PeopleCounterData;
import org.objectspace.rfid.headless.payload.TagReadData;
import org.objectspace.rfid.headless.payload.TagsClearedData;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.PayloadRegistry;
import org.objectspace.wsbrokerclient.event.RawEvent;

import java.util.List;

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

    @Test
    @DisplayName("Test GateStatusData and PeopleCounterData model properties and summary messages")
    void testGateStatusAndPeopleCounterPayloadModelAndSummary() {
        FeigRFID.GateAddress addr = new FeigRFID.GateAddress(1, 0);
        FeigRFID.GateCounter counter = new FeigRFID.GateCounter(addr, 10, 5, 2, 1);
        FeigRFID.GateDiagnostic diag = new FeigRFID.GateDiagnostic(addr, 0, 1);

        GateStatusData statusData = new GateStatusData("gate-01", List.of(counter), List.of(diag));
        assertEquals("gate-01", statusData.readerId());
        assertEquals(1, statusData.counters().size());
        assertEquals(10, statusData.counters().get(0).detector1Counter1());
        assertEquals(1, statusData.diagnostics().size());
        assertEquals("ok", statusData.status());
        assertTrue(statusData.getSummaryMessage().contains("[GATE STATUS]"));
        assertTrue(statusData.getSummaryMessage().contains("gate-01"));
        assertTrue(statusData.getSummaryMessage().contains("c1=10"));
        assertTrue(statusData.getSummaryMessage().contains("c2=5"));

        Event<GateStatusData> event = Event.of(GateStatusData.EVENT_NAME, "test-client", "gates", statusData);
        JsonNode node = payloadData(event);
        assertEquals("gate-01", node.get("readerId").asText());
        assertEquals(1, node.get("counters").size());
        assertEquals(10, node.get("counters").get(0).get("detector1Counter1").asLong());

        // Test unmarshaling via PayloadRegistry
        HeadlessWsBrokerDispatcher.registerPayloads();
        RawEvent rawEvent = new RawEvent();
        rawEvent.setType(GateStatusData.EVENT_NAME);
        rawEvent.setData(node);
        Object unmarshaled = PayloadRegistry.unmarshalDynamic(rawEvent);
        assertTrue(unmarshaled instanceof GateStatusData);
        GateStatusData decoded = (GateStatusData) unmarshaled;
        assertEquals("gate-01", decoded.readerId());
        assertEquals(1, decoded.counters().size());

        // Test PeopleCounterData
        PeopleCounterData pcData = PeopleCounterData.fromGateStatusData(statusData);
        assertNotNull(pcData);
        assertEquals("gate-01", pcData.readerId());
        assertEquals(1, pcData.counters().size());
        assertTrue(pcData.getSummaryMessage().contains("[PEOPLE COUNTER]"));
        assertTrue(pcData.getSummaryMessage().contains("c1=10"));
        assertTrue(pcData.getSummaryMessage().contains("c2=5"));

        Event<PeopleCounterData> pcEvent = Event.of(PeopleCounterData.EVENT_NAME, "test-client", "gates", pcData);
        RawEvent pcRaw = new RawEvent();
        pcRaw.setType(PeopleCounterData.EVENT_NAME);
        pcRaw.setData(payloadData(pcEvent));
        Object pcUnmarshaled = PayloadRegistry.unmarshalDynamic(pcRaw);
        assertTrue(pcUnmarshaled instanceof PeopleCounterData);
    }
}

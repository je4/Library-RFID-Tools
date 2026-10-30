package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WsBrokerPayloadParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "'07', 7",
            "'0x07', 7",
            "'0X07', 7",
            "'7', 7",
            "'C2', -62",
            "'0xC2', -62"
    })
    @DisplayName("Test parseAfi parses various string representations")
    void testParseAfiStrings(String input, int expected) {
        Byte b = WsBrokerPayloadParser.parseAfi(input);
        assertNotNull(b);
        assertEquals((byte) expected, b.byteValue());
    }

    @Test
    @DisplayName("Test parseAfi handles numbers, null, and empty strings")
    void testParseAfiEdgeCases() {
        assertEquals((byte) 7, WsBrokerPayloadParser.parseAfi(7).byteValue());
        assertEquals((byte) 7, WsBrokerPayloadParser.parseAfi(7L).byteValue());
        assertNull(WsBrokerPayloadParser.parseAfi(null));
        assertNull(WsBrokerPayloadParser.parseAfi(""));
        assertNull(WsBrokerPayloadParser.parseAfi("   "));
    }

    @Test
    @DisplayName("Test formatAfiHex formats bytes to 2-digit uppercase hex")
    void testFormatAfiHex() {
        assertNull(WsBrokerPayloadParser.formatAfiHex(null));
        assertEquals("07", WsBrokerPayloadParser.formatAfiHex((byte) 7));
        assertEquals("C2", WsBrokerPayloadParser.formatAfiHex((byte) 0xC2));
        assertEquals("00", WsBrokerPayloadParser.formatAfiHex((byte) 0));
        assertEquals("FF", WsBrokerPayloadParser.formatAfiHex((byte) 0xFF));
    }

    @Test
    @DisplayName("Test parseTagWriteItems with array format")
    void testParseTagWriteItemsArray() throws Exception {
        String json = """
            [
                {"uid": "E00401001111", "itemId": "ITEM-1", "afi": "07"},
                {"uid": "E00401002222", "itemId": "ITEM-2", "afi": 7}
            ]
            """;
        JsonNode node = mapper.readTree(json);
        List<TagWriteItem> items = WsBrokerPayloadParser.parseTagWriteItems(node);
        assertEquals(2, items.size());
        assertEquals("E00401001111", items.get(0).getUid());
        assertEquals("ITEM-1", items.get(0).getItemId());
        assertEquals("E00401002222", items.get(1).getUid());
        assertEquals("ITEM-2", items.get(1).getItemId());
    }

    @Test
    @DisplayName("Test parseTagWriteItems with map wrapper format")
    void testParseTagWriteItemsMapWrapper() throws Exception {
        String json = """
            {
                "tags": {
                    "E00401001111": {"itemId": "ITEM-1", "country": "CH"},
                    "E00401002222": "ITEM-2"
                },
                "isil": "001596-0"
            }
            """;
        JsonNode node = mapper.readTree(json);
        List<TagWriteItem> items = WsBrokerPayloadParser.parseTagWriteItems(node);
        assertEquals(2, items.size());
        assertEquals("E00401001111", items.get(0).getUid());
        assertEquals("ITEM-1", items.get(0).getItemId());
        assertEquals("001596-0", items.get(0).getIsil());
        assertEquals("E00401002222", items.get(1).getUid());
        assertEquals("ITEM-2", items.get(1).getItemId());
        assertEquals("001596-0", items.get(1).getIsil());
    }

    @Test
    @DisplayName("Test parseTagWriteItems with root-level single item")
    void testParseTagWriteItemsSingleItem() throws Exception {
        String json = """
            {
                "uid": "E00401009999",
                "itemId": "BOOK-SINGLE",
                "isil": "001596-0",
                "country": "CH",
                "afi": "07"
            }
            """;
        JsonNode node = mapper.readTree(json);
        List<TagWriteItem> items = WsBrokerPayloadParser.parseTagWriteItems(node);
        assertEquals(1, items.size());
        assertEquals("E00401009999", items.get(0).getUid());
        assertEquals("BOOK-SINGLE", items.get(0).getItemId());
        assertEquals("001596-0", items.get(0).getIsil());
        assertEquals("CH", items.get(0).getCountry());
    }

    @Test
    @DisplayName("Test isAddressedToUs and requested reader ID extraction")
    void testAddressingAndReaderId() {
        Event<String> evt = Event.createMessage("client-a", "reader-01", "hello");
        assertTrue(WsBrokerPayloadParser.isAddressedToUs(evt, "hub", List.of("ch1"), Map.of("reader-01", new HeadlessReaderInstance(new HeadlessReaderConfig("reader-01"), null, null, null))));
        assertFalse(WsBrokerPayloadParser.isAddressedToUs(evt, "hub", List.of("ch1"), Map.of("reader-02", new HeadlessReaderInstance(new HeadlessReaderConfig("reader-02"), null, null, null))));
        assertEquals("reader-01", WsBrokerPayloadParser.extractRequestedReaderId(null, evt, "hub", List.of("ch1")));
    }
}

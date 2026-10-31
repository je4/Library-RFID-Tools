package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.headless.payload.TagReadData;
import org.objectspace.rfid.headless.payload.TagsClearedData;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessWsBrokerDispatcherTest {

    private static JsonNode payloadData(Event<?> evt) {
        return evt.getDataNode();
    }

    @Test
    @DisplayName("Test dispatcher is disabled when no wsbroker endpoint is configured")
    void testDispatcherDisabledWhenNoEndpoint() {
        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("test-client", null, List.of("all"), null);
        assertFalse(dispatcher.isActive());
        assertNull(dispatcher.getEndpoint());
        assertNull(dispatcher.getClient());
        dispatcher.close();

        HeadlessWsBrokerDispatcher blankDispatcher = new HeadlessWsBrokerDispatcher("test-client", "   ", List.of("all"), null);
        assertFalse(blankDispatcher.isActive());
        blankDispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher initializes client configuration with endpoint, name, channels, and clienttls")
    void testDispatcherInitialization() {
        CertManagerConfig certConfig = new CertManagerConfig();
        certConfig.setType(LoaderType.DEV);

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "custom-reader",
                "https://broker.example.com:8765",
                List.of("channel1", "channel2"),
                certConfig
        );

        assertTrue(dispatcher.isActive());
        assertEquals("custom-reader", dispatcher.getClientName());
        assertEquals("https://broker.example.com:8765", dispatcher.getEndpoint());
        assertEquals(List.of("channel1", "channel2"), dispatcher.getChannels());
        assertNotNull(dispatcher.getClient());
        assertNotNull(dispatcher.getCertLoader());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatchTag sends formatted Event to all configured channels")
    void testDispatchTagSendsToAllChannels() {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all", "intern", "station-1"),
                null,
                mockClient
        );

        assertTrue(dispatcher.isActive());

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-12345", "DE", "DE-101", new ArrayList<>());

        dispatcher.dispatchTag(
                "E004015011223344",
                "BOOK-12345",
                "0102030405",
                "DE-101",
                "DE",
                true,
                "SLIX",
                "test-session",
                System.currentTimeMillis(),
                "rfid-station-1",
                model
        );

        assertEquals(3, sentEvents.size(), "Should send event to all 3 channels");

        for (int i = 0; i < 3; i++) {
            Event e = sentEvents.get(i);
            assertEquals("rfid-station-1", e.getSource());
            assertEquals("rfid/tag_read", e.getType());
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains("[TAG READ]"));
            assertTrue(e.getMessage().contains("BOOK-12345"));
            assertTrue(e.getMessage().contains("E004015011223344"));
            assertTrue(e.getMessage().contains("DE-101"));

            JsonNode data = payloadData(e);
            assertNotNull(data);
            assertEquals("E004015011223344", data.get("uid").asText());
            assertEquals("BOOK-12345", data.get("itemId").asText());
            assertEquals("0102030405", data.get("raw").asText());
            assertEquals("DE-101", data.get("isil").asText());
            assertEquals("DE", data.get("country").asText());
            assertTrue(data.get("crcOk").asBoolean());
            assertEquals("OK", data.get("crc").asText());
            assertEquals("SLIX", data.get("tagName").asText());
            assertEquals(1, data.get("version").asInt());
            assertEquals(1, data.get("usageType").asInt());
            assertEquals(1, data.get("partNo").asInt());
            assertEquals(1, data.get("parts").asInt());
        }

        assertEquals("all", sentEvents.get(0).getTarget());
        assertEquals("intern", sentEvents.get(1).getTarget());
        assertEquals("station-1", sentEvents.get(2).getTarget());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test loading from inventory.xml containing nested wsbroker and mTLS configuration")
    void testLoadFromInventoryXml() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <active>true</active>
                        <url>https://broker.local:8765</url>
                        <channels>
                            <channel>rfid-events</channel>
                            <channel>admin</channel>
                        </channels>
                        <clienttls>
                            <type>dev</type>
                            <directory>certs</directory>
                            <active>true</active>
                        </clienttls>
                    </wsbroker>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-test-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xml);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        HeadlessWsBrokerDispatcher dispatcher = HeadlessWsBrokerDispatcher.fromConfiguration(config);

        assertTrue(dispatcher.isActive());
        assertEquals("https://broker.local:8765", dispatcher.getEndpoint());
        assertEquals(List.of("rfid-events", "admin"), dispatcher.getChannels());
        assertNotNull(dispatcher.getCertLoader());
        assertNotNull(dispatcher.getClient());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test fromConfiguration when wsbroker active flag is false")
    void testFromConfigurationDisabledWhenActiveFalse() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <active>false</active>
                        <url>https://broker.local:8765</url>
                        <channels>
                            <channel>rfid-events</channel>
                        </channels>
                    </wsbroker>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-test-disabled-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xml);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        HeadlessWsBrokerDispatcher dispatcher = HeadlessWsBrokerDispatcher.fromConfiguration(config);

        assertFalse(dispatcher.isActive());
        assertNull(dispatcher.getClient());
        dispatcher.close();
    }

    @Test
    @DisplayName("Test integration: HeadlessReaderCallback dispatches to both Webservice and WSBroker WebSocket channels")
    void testCallbackDispatchesBothWebserviceAndWsBroker() throws Exception {
        AtomicInteger wsCount = new AtomicInteger(0);
        List<Event> wsEvents = new CopyOnWriteArrayList<>();

        // 1. Mock Webservice dispatcher
        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:8080/scan", "POST", "secret", false);
        WebserviceDispatcher mockWsDispatcher = new WebserviceDispatcher(wsCfg) {
            @Override
            public WebserviceResponse dispatchScan(String marker, String userText, String nfcContent, String rawPayloadHex,
                                                    String uid, FinnishDataModel libraryData, String session, String deviceName) {
                wsCount.incrementAndGet();
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:8080/scan", 5, "secret", Map.of(), "{}", "");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        TagScanProcessor processor = new TagScanProcessor(cfg) {
            @Override
            public WebserviceDispatcher getWebserviceDispatcher() {
                return mockWsDispatcher;
            }

            @Override
            public WebserviceResponse dispatchWebservice(String marker, String itemId, String rawHex, String uid,
                                                        FinnishDataModel metadata, String sessionName, String extra) throws Exception {
                return mockWsDispatcher.dispatchScan(marker, marker, itemId, rawHex, uid, metadata, sessionName, extra);
            }
        };

        // 2. Mock WSBroker client & dispatcher
        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-dual-station");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockBrokerClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                wsEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher brokerDispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-dual-station",
                "https://localhost:8765",
                List.of("all", "dual-channel"),
                null,
                mockBrokerClient
        );

        // 3. Callback with both processor and broker dispatcher
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, brokerDispatcher, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-DUAL-999", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);

        callback.doIt(1, 1, "NXP", "SLIX", "E004010099998888", data, 4);

        // Verify Webservice received dispatch
        assertEquals(1, wsCount.get(), "Webservice dispatch should have been triggered once");

        // Verify WSBroker received 2 events (for channels 'all' and 'dual-channel')
        assertEquals(2, wsEvents.size(), "WSBroker should have received events on both channels");
        assertEquals("all", wsEvents.get(0).getTarget());
        assertEquals("dual-channel", wsEvents.get(1).getTarget());
        assertEquals("BOOK-DUAL-999", payloadData(wsEvents.get(0)).get("itemId").asText());
        assertEquals("E004010099998888", payloadData(wsEvents.get(0)).get("uid").asText());

        brokerDispatcher.close();
    }

    @Test
    @DisplayName("Test dispatchEmpty sends tags_cleared Event to all configured channels")
    void testDispatchEmptySendsToAllChannels() {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all", "intern"),
                null,
                mockClient
        );

        assertTrue(dispatcher.isActive());

        dispatcher.dispatchEmpty("test-session", System.currentTimeMillis(), "rfid-station-1");

        assertEquals(2, sentEvents.size(), "Should send tags_cleared event to all 2 channels");

        for (int i = 0; i < 2; i++) {
            Event e = sentEvents.get(i);
            assertEquals("rfid-station-1", e.getSource());
            assertEquals("rfid/tags_cleared", e.getType());
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage().contains("[READER CLEARED]"));
            assertTrue(e.getMessage().contains("rfid-station-1"));

            JsonNode data = payloadData(e);
            assertNotNull(data);
            assertEquals("empty", data.get("status").asText());
            assertEquals(0, data.get("totalTags").asInt());
            assertEquals("rfid-station-1", data.get("readerId").asText());
        }

        assertEquals("all", sentEvents.get(0).getTarget());
        assertEquals("intern", sentEvents.get(1).getTarget());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test HeadlessReaderCallback dispatches empty event on edge transition (tags present -> no tags)")
    void testCallbackDispatchesEmptyOnEdgeTransition() throws Exception {
        List<Event> wsEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid:station:01");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                wsEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher wsDispatcher = new HeadlessWsBrokerDispatcher(
                "rfid:station:01",
                "https://localhost:8765",
                List.of("all"),
                null,
                mockClient
        );

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        // debounceWindow = 3000ms, emptyTimeoutMs = 150ms
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, wsDispatcher, 3000L, 150L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-TRANSITION-01", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);

        // 1. Initial tag scan: transitions from EMPTY -> TAGS_PRESENT
        callback.doIt(1, 1, "NXP", "SLIX", "E004010011112222", data, 4);

        assertTrue(callback.hasActiveTags());
        assertEquals(1, wsEvents.size());
        Event tagReadEvent = wsEvents.get(0);
        assertEquals("rfid:tag_read", tagReadEvent.getDataNode().get("event").asText());
        assertEquals("E004010011112222", payloadData(tagReadEvent).get("uid").asText());

        // 2. Wait for empty timeout (> 150ms) without scan -> transitions from TAGS_PRESENT -> EMPTY
        Thread.sleep(250);

        assertFalse(callback.hasActiveTags(), "Tags should have timed out and cleared");
        assertEquals(2, wsEvents.size(), "Should have dispatched rfid:tags_cleared event on edge transition");

        Event clearedEvent = wsEvents.get(1);
        assertEquals("rfid:tags_cleared", clearedEvent.getDataNode().get("event").asText());
        assertEquals("empty", payloadData(clearedEvent).get("status").asText());
        assertEquals(0, payloadData(clearedEvent).get("totalTags").asInt());
        assertEquals("rfid:station:01", payloadData(clearedEvent).get("readerId").asText());

        // 3. Repeated empty timeout should NOT dispatch another empty event (edge transition only)
        Thread.sleep(200);
        assertEquals(2, wsEvents.size(), "No duplicate empty event should be dispatched while remaining empty");

        // 4. New tag scan after being empty -> transitions from EMPTY -> TAGS_PRESENT again
        callback.doIt(1, 1, "NXP", "SLIX", "E004010033334444", data, 4);
        assertTrue(callback.hasActiveTags());
        assertEquals(3, wsEvents.size(), "New scan should dispatch another tag_read event");
        assertEquals("E004010033334444", payloadData(wsEvents.get(2)).get("uid").asText());

        wsDispatcher.close();
    }

    @Test
    @DisplayName("Test dispatchTag with AFI broadcasts rfid:tag_read containing afi to all channels")
    void testDispatchTagWithAfiToMultipleChannels() {
        List<Event> sentEvents = new ArrayList<>();
        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("all", "gates"),
                null,
                mockClient
        );

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-AFI-01", "CH", "001596-0", new ArrayList<>());

        dispatcher.dispatchTag(
                "E0040150AABBCCDD",
                "BOOK-AFI-01",
                "010203",
                "001596-0",
                "CH",
                true,
                "SLIX2",
                "test-session",
                System.currentTimeMillis(),
                "rfid-station-1",
                model,
                "07"
        );

        assertEquals(2, sentEvents.size());

        for (Event e : sentEvents) {
            assertEquals("rfid/tag_read", e.getType());
            assertTrue(e.getMessage().contains("AFI: 07"));
            JsonNode data = payloadData(e);
            assertEquals("07", data.get("afi").asText());
            assertEquals("E0040150AABBCCDD", data.get("uid").asText());
            assertEquals("BOOK-AFI-01", data.get("itemId").asText());
        }

        dispatcher.close();
    }

    @Test
    @DisplayName("Test polymorphic dispatch with strongly typed data records")
    void testPolymorphicDispatch() {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-station-1");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1",
                "https://localhost:8765",
                List.of("ch1"),
                null,
                mockClient
        );

        TagReadData tagData = new TagReadData(
                "UID-999", "ITEM-999", "AABBCC", "DE-101", "DE",
                true, "SLIX2", 4, 1, 0, 1, null, "Station-1"
        );

        TagsClearedData clearedData = new TagsClearedData("Station-1");

        dispatcher.dispatchTag(tagData);
        dispatcher.dispatchEmpty(clearedData);

        assertEquals(2, sentEvents.size());

        Event e1 = sentEvents.get(0);
        assertEquals("rfid/tag_read", e1.getType());
        assertEquals("UID-999", payloadData(e1).get("uid").asText());
        assertEquals("ITEM-999", payloadData(e1).get("itemId").asText());
        assertEquals(4, payloadData(e1).get("parts").asInt());
        assertEquals(1, payloadData(e1).get("partNo").asInt());
        assertEquals(0, payloadData(e1).get("usageType").asInt());
        assertEquals(1, payloadData(e1).get("version").asInt());
        assertTrue(e1.getMessage().contains("[TAG READ]"));

        Event e2 = sentEvents.get(1);
        assertEquals("rfid/tags_cleared", e2.getType());
        assertEquals("empty", payloadData(e2).get("status").asText());
        assertEquals(0, payloadData(e2).get("totalTags").asInt());
        assertTrue(e2.getMessage().contains("[READER CLEARED]"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test active tag scan followed by reader disconnect dispatches rfid:tags_cleared to WSBroker")
    public void testTagScanFollowedByReaderDisconnectDispatchesClearedEvent() throws Exception {
        List<Event> wsEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid:station:01");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                wsEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher wsDispatcher = new HeadlessWsBrokerDispatcher(
                "rfid:station:01",
                "https://localhost:8765",
                List.of("all"),
                null,
                mockClient
        );

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, wsDispatcher, 3000L);

        // 1. Scan a tag -> broadcasts rfid:tag_read
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-DISCONNECT-99", "DE", "DE-101", new ArrayList<>());
        callback.doIt(1, 1, "NXP", "SLIX", "E004019988776655", model.getBlock(32), 4);

        assertTrue(callback.hasActiveTags());
        assertEquals(1, wsEvents.size());
        assertEquals("rfid:tag_read", wsEvents.get(0).getDataNode().get("event").asText());
        assertEquals("E004019988776655", payloadData(wsEvents.get(0)).get("uid").asText());

        // 2. Reader disconnect occurs
        callback.onReaderDisconnected();

        assertFalse(callback.hasActiveTags());
        assertEquals(2, wsEvents.size());
        Event clearedEvent = wsEvents.get(1);
        assertEquals("rfid:tags_cleared", clearedEvent.getDataNode().get("event").asText());
        assertEquals("empty", payloadData(clearedEvent).get("status").asText());
        assertEquals(0, payloadData(clearedEvent).get("totalTags").asInt());

        wsDispatcher.close();
    }

    @Test
    @DisplayName("Test WSBroker channel deduplication across global and reader configs")
    void testChannelDeduplication() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <active>true</active>
                        <url>https://localhost:8765</url>
                        <channels>
                            <channel>common</channel>
                            <channel>gates</channel>
                        </channels>
                    </wsbroker>
                    <readers>
                        <reader>
                            <reader_id>gate-01</reader_id>
                            <channels>
                                <channel>gates</channel>
                                <channel>gate-01-ch</channel>
                            </channels>
                        </reader>
                        <reader>
                            <reader_id>gate-02</reader_id>
                            <channels>
                                <channel>common</channel>
                                <channel>gate-02-ch</channel>
                            </channels>
                        </reader>
                    </readers>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-ch-dedup-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        HeadlessWsBrokerDispatcher dispatcher = HeadlessWsBrokerDispatcher.fromConfiguration(config);

        List<String> channels = dispatcher.getChannels();
        assertEquals(4, channels.size());
        assertEquals(List.of("common", "gates", "gate-01-ch", "gate-02-ch"), channels);
        dispatcher.close();
    }

    @Test
    @DisplayName("Test per-reader targeted channel dispatching")
    void testPerReaderTargetedChannelDispatching() {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("rfid-hub");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-hub",
                "https://localhost:8765",
                List.of("global-ch"),
                null,
                mockClient,
                true
        );

        HeadlessReaderConfig cfg1 = new HeadlessReaderConfig("reader-1");
        cfg1.setChannels(List.of("channel-r1-a", "channel-r1-b"));

        HeadlessReaderConfig cfg2 = new HeadlessReaderConfig("reader-2");
        cfg2.setChannels(List.of("channel-r2"));

        HeadlessReaderConfig cfgFallback = new HeadlessReaderConfig("reader-fallback");
        // No reader-specific channels set -> fallback to global

        HeadlessReaderInstance inst1 = new HeadlessReaderInstance(cfg1, null, null, null);
        HeadlessReaderInstance inst2 = new HeadlessReaderInstance(cfg2, null, null, null);
        HeadlessReaderInstance instFallback = new HeadlessReaderInstance(cfgFallback, null, null, null);

        dispatcher.setReaderInstances(List.of(inst1, inst2, instFallback));

        // 1. Dispatch TagReadData from reader-1
        TagReadData p1 = new TagReadData("UID1", "ITEM1", "", "", "", true, "SLIX", null, null, "reader-1");
        dispatcher.dispatchTag(p1);

        assertEquals(2, sentEvents.size());
        assertEquals("channel-r1-a", sentEvents.get(0).getTarget());
        assertEquals("channel-r1-b", sentEvents.get(1).getTarget());
        sentEvents.clear();

        // 2. Dispatch TagReadData from reader-2
        TagReadData p2 = new TagReadData("UID2", "ITEM2", "", "", "", true, "SLIX", null, null, "reader-2");
        dispatcher.dispatchTag(p2);

        assertEquals(1, sentEvents.size());
        assertEquals("channel-r2", sentEvents.get(0).getTarget());
        sentEvents.clear();

        // 3. Dispatch TagReadData from fallback reader
        TagReadData pFallback = new TagReadData("UID3", "ITEM3", "", "", "", true, "SLIX", null, null, "reader-fallback");
        dispatcher.dispatchTag(pFallback);

        assertEquals(1, sentEvents.size());
        assertEquals("global-ch", sentEvents.get(0).getTarget());

        dispatcher.close();
    }
}

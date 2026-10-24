package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.headless.payload.BaseRfidPayload;
import org.objectspace.rfid.headless.payload.DeviceMetadataPayload;
import org.objectspace.rfid.headless.payload.TagReadPayload;
import org.objectspace.rfid.headless.payload.TagsClearedPayload;
import org.objectspace.rfid.headless.payload.WriteAfiResponsePayload;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessWsBrokerDispatcherTest {

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
                "E004015099887766",
                "BOOK-12345",
                "0102030405060708",
                "DE-101",
                "DE",
                true,
                "ISO 15693",
                "Session-01",
                System.currentTimeMillis(),
                "1",
                model
        );

        assertEquals(3, sentEvents.size(), "Should have dispatched an event to each of the 3 channels");

        for (int i = 0; i < sentEvents.size(); i++) {
            Event evt = sentEvents.get(i);
            assertEquals("rfid-station-1", evt.getSource());
            assertEquals("message", evt.getType());
            assertNotNull(evt.getData());
            assertEquals("rfid:tag_read", evt.getData().get("event").asText());
            assertTrue(evt.getData().has("uid"));
            assertEquals("E004015099887766", evt.getData().get("uid").asText());
            assertEquals("BOOK-12345", evt.getData().get("itemId").asText());
            assertEquals("DE-101", evt.getData().get("isil").asText());
            assertEquals("DE", evt.getData().get("country").asText());
            assertTrue(evt.getData().get("crcOk").asBoolean());
            assertTrue(evt.getMessage().contains("BOOK-12345"));
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
                        <name>test-java</name>
                        <url><![CDATA[https://localhost:8765]]></url>
                        <channels>
                            <channel>all</channel>
                            <channel>intern</channel>
                        </channels>
                        <clienttls>
                            <type>dev</type>
                        </clienttls>
                    </wsbroker>
                </configuration>
                """;

        File tempFile = File.createTempFile("inventory-wsbroker-test-", ".xml");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(xml);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempFile.getAbsolutePath());
        assertTrue(HeadlessConfigHelper.isWsBrokerActive(config));
        assertEquals("test-java", HeadlessConfigHelper.getClientName(config));
        assertEquals("https://localhost:8765", HeadlessConfigHelper.getWsBrokerEndpoint(config));
        assertEquals(List.of("all", "intern"), HeadlessConfigHelper.getChannels(config));

        CertManagerConfig tlsCfg = HeadlessConfigHelper.getClientTlsConfig(config);
        assertNotNull(tlsCfg);
        assertEquals(LoaderType.DEV, tlsCfg.getType());

        HeadlessWsBrokerDispatcher dispatcher = HeadlessWsBrokerDispatcher.fromConfiguration(config);
        assertTrue(dispatcher.isActive());
        assertEquals("test-java", dispatcher.getClientName());
        assertEquals("https://localhost:8765", dispatcher.getEndpoint());
        assertEquals(List.of("all", "intern"), dispatcher.getChannels());
        assertNotNull(dispatcher.getCertLoader());

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
                        <name>disabled-reader</name>
                        <url>https://localhost:8765</url>
                    </wsbroker>
                </configuration>
                """;

        File tempFile = File.createTempFile("inventory-wsbroker-disabled-", ".xml");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(xml);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempFile.getAbsolutePath());
        assertFalse(HeadlessConfigHelper.isWsBrokerActive(config));

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

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("test-java");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                wsEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher wsDispatcher = new HeadlessWsBrokerDispatcher(
                "test-java",
                "https://localhost:8765",
                List.of("all", "intern"),
                null,
                mockClient
        );

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:8080/scan", "POST", "secret", false);
        WebserviceDispatcher mockWebserviceDispatcher = new WebserviceDispatcher(wsCfg) {
            @Override
            public WebserviceResponse dispatchScan(String marker, String userText, String nfcContent,
                                                   String rawPayloadHex, String uid, FinnishDataModel libraryData,
                                                   String session, String deviceName) {
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
                return mockWebserviceDispatcher;
            }

            @Override
            public WebserviceResponse dispatchWebservice(String marker, String itemId, String rawHex, String uid,
                                                         FinnishDataModel metadata, String sessionName, String extra) throws Exception {
                return mockWebserviceDispatcher.dispatchScan(marker, marker, itemId, rawHex, uid, metadata, sessionName, extra);
            }
        };

        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, wsDispatcher, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-42", "DE", "DE-101", new ArrayList<>());
        byte[] data = model.getBlock(32);

        callback.doIt(1, 1, "NXP", "SLIX", "UID-42", data, 4);

        // Verify Webservice call
        assertEquals(1, wsCount.get(), "Webservice should have been called once");

        // Verify WSBroker WebSocket channels call
        assertEquals(2, wsEvents.size(), "WSBroker events should have been sent to 2 channels");
        assertEquals("all", wsEvents.get(0).getTarget());
        assertEquals("intern", wsEvents.get(1).getTarget());
        assertEquals("rfid:tag_read", wsEvents.get(0).getData().get("event").asText());
        assertEquals("ITEM-42", wsEvents.get(0).getData().get("itemId").asText());
        assertEquals("UID-42", wsEvents.get(0).getData().get("uid").asText());

        callback.close();
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
                List.of("all", "station-1"),
                null,
                mockClient
        );

        assertTrue(dispatcher.isActive());

        dispatcher.dispatchEmpty("Session-99", 1234567890L, "Station-Client-1");

        assertEquals(2, sentEvents.size(), "Should have dispatched cleared event to both channels");

        for (Event evt : sentEvents) {
            assertEquals("rfid-station-1", evt.getSource());
            assertEquals("message", evt.getType());
            assertNotNull(evt.getData());
            assertEquals("rfid:tags_cleared", evt.getData().get("event").asText());
            assertEquals("empty", evt.getData().get("status").asText());
            assertEquals(0, evt.getData().get("totalTags").asInt());
            assertEquals("Session-99", evt.getData().get("session").asText());
            assertEquals(1234567890L, evt.getData().get("timestamp").asLong());
            assertEquals("Station-Client-1", evt.getData().get("clientId").asText());
            assertTrue(evt.getMessage().contains("[READER CLEARED]"));
        }

        assertEquals("all", sentEvents.get(0).getTarget());
        assertEquals("station-1", sentEvents.get(1).getTarget());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test HeadlessReaderCallback dispatches empty event on edge transition (tags present -> no tags)")
    void testCallbackDispatchesEmptyOnEdgeTransition() throws Exception {
        List<Event> wsEvents = new CopyOnWriteArrayList<>();

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("test-edge-reader");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                wsEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher wsDispatcher = new HeadlessWsBrokerDispatcher(
                "test-edge-reader",
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

        // 1. Initial state: empty() should not dispatch event if no tags were active
        assertFalse(callback.hasActiveTags());
        callback.empty();
        assertEquals(0, callback.getClearedEvents());
        assertEquals(0, wsEvents.size());

        // 2. Tag placed on antenna -> doIt called
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-100", "DE", "DE-101", new ArrayList<>());
        callback.doIt(1, 1, "NXP", "SLIX", "UID-100", model.getBlock(32), 4);

        assertTrue(callback.hasActiveTags());
        assertEquals(1, wsEvents.size(), "Should have 1 tag read event");
        assertEquals("rfid:tag_read", wsEvents.get(0).getData().get("event").asText());
        assertEquals("UID-100", wsEvents.get(0).getData().get("uid").asText());

        // 3. Tag removed from antenna -> empty() called
        callback.empty();
        assertFalse(callback.hasActiveTags());
        assertEquals(1, callback.getClearedEvents());
        assertEquals(2, wsEvents.size(), "Should have received 1 tag read + 1 cleared event");
        assertEquals("rfid:tags_cleared", wsEvents.get(1).getData().get("event").asText());

        // 4. Repeated empty() cycles while reader remains empty -> must NOT send duplicate cleared events
        callback.empty();
        callback.empty();
        assertEquals(1, callback.getClearedEvents());
        assertEquals(2, wsEvents.size());

        // 5. Place tag again and remove again -> should send another cleared event
        callback.doIt(1, 1, "NXP", "SLIX", "UID-101", model.getBlock(32), 4);
        assertTrue(callback.hasActiveTags());
        assertEquals(3, wsEvents.size());

        callback.empty();
        assertFalse(callback.hasActiveTags());
        assertEquals(2, callback.getClearedEvents());
        assertEquals(4, wsEvents.size());
        assertEquals("rfid:tags_cleared", wsEvents.get(3).getData().get("event").asText());

        callback.close();
    }

    @Test
    @DisplayName("Test dispatchTag with AFI broadcasts rfid:tag_read containing afi to all channels")
    void testDispatchTagWithAfiToMultipleChannels() {
        List<Event> sentEvents = new ArrayList<>();
        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("test-station");
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "test-station",
                "https://localhost:8765",
                List.of("all", "channel-1"),
                null,
                mockClient
        );

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-AFI-1", "CH", "001596-0", new ArrayList<>());

        dispatcher.dispatchTag(
                "E004010099887766",
                "BOOK-AFI-1",
                "01020304",
                "001596-0",
                "CH",
                true,
                "ISO 15693 : NXP I-Code SLIX",
                "Session-AFI",
                System.currentTimeMillis(),
                "Client-1",
                model,
                "07"
        );

        assertEquals(2, sentEvents.size(), "Should dispatch to both configured channels");

        for (Event e : sentEvents) {
            assertEquals("rfid:tag_read", e.getData().get("event").asText());
            assertEquals("E004010099887766", e.getData().get("uid").asText());
            assertEquals("BOOK-AFI-1", e.getData().get("itemId").asText());
            assertEquals("07", e.getData().get("afi").asText());
            assertTrue(e.getMessage().contains("AFI: 07"));
        }

        dispatcher.close();
    }

    @Test
    @DisplayName("Test polymorphic dispatch with BaseRfidPayload instances")
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

        TagReadPayload tagPayload = new TagReadPayload(
                "UID-999", "ITEM-999", "AABBCC", "DE-101", "DE",
                true, "SLIX2", "Session-Poly", 1000L, "Station-1",
                4, 1, 0, 1
        );

        TagsClearedPayload clearedPayload = new TagsClearedPayload("Session-Poly", 2000L, "Station-1");

        // Polymorphic dispatch via BaseRfidPayload reference
        BaseRfidPayload p1 = tagPayload;
        BaseRfidPayload p2 = clearedPayload;

        dispatcher.dispatch(p1);
        dispatcher.dispatch(p2);

        assertEquals(2, sentEvents.size());

        Event e1 = sentEvents.get(0);
        assertEquals("rfid:tag_read", e1.getData().get("event").asText());
        assertEquals("UID-999", e1.getData().get("uid").asText());
        assertEquals("ITEM-999", e1.getData().get("itemId").asText());
        assertEquals(4, e1.getData().get("parts").asInt());
        assertEquals(1, e1.getData().get("partNo").asInt());
        assertEquals(0, e1.getData().get("usageType").asInt());
        assertEquals(1, e1.getData().get("version").asInt());
        assertTrue(e1.getMessage().contains("[TAG READ]"));

        Event e2 = sentEvents.get(1);
        assertEquals("rfid:tags_cleared", e2.getData().get("event").asText());
        assertEquals("empty", e2.getData().get("status").asText());
        assertEquals(0, e2.getData().get("totalTags").asInt());
        assertTrue(e2.getMessage().contains("[READER CLEARED]"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test TagReadPayload and TagsClearedPayload model properties and summary messages")
    void testPayloadModelAndSummary() {
        TagReadPayload tagPayload = new TagReadPayload(
                "UID-1", "ITEM-1", "HEX1", "ISIL1", "DE",
                true, "TagType", "Sess1", 12345L, "Client1",
                null
        );

        assertEquals("rfid:tag_read", tagPayload.getEvent());
        assertEquals("UID-1", tagPayload.getUid());
        assertEquals("ITEM-1", tagPayload.getItemId());
        assertEquals("HEX1", tagPayload.getRaw());
        assertEquals("ISIL1", tagPayload.getIsil());
        assertEquals("DE", tagPayload.getCountry());
        assertTrue(tagPayload.isCrcOk());
        assertTrue(tagPayload.isCrcValid());
        assertEquals("OK", tagPayload.getCrc());
        assertEquals("TagType", tagPayload.getTagName());
        assertEquals("Sess1", tagPayload.getSession());
        assertEquals(12345L, tagPayload.getTimestamp());
        assertEquals("Client1", tagPayload.getClientId());
        assertNull(tagPayload.getParts());
        assertNull(tagPayload.getAfi());
        assertTrue(tagPayload.getSummaryMessage().contains("UID: UID-1"));
        assertFalse(tagPayload.getSummaryMessage().contains("AFI:"));

        Event eNoAfi = Event.createMessage("Client1", "ch1", tagPayload);
        assertFalse(eNoAfi.getData().has("afi"), "Null AFI should be omitted from JSON");

        // With AFI
        TagReadPayload tagPayloadWithAfi = new TagReadPayload(
                "UID-1", "ITEM-1", "HEX1", "ISIL1", "DE",
                true, "TagType", "Sess1", 12345L, "Client1",
                (FinnishDataModel) null, "07"
        );
        assertEquals("07", tagPayloadWithAfi.getAfi());
        assertTrue(tagPayloadWithAfi.getSummaryMessage().contains("AFI: 07"));

        Event eWithAfi = Event.createMessage("Client1", "ch1", tagPayloadWithAfi);
        assertTrue(eWithAfi.getData().has("afi"), "AFI should be present in JSON when set");
        assertEquals("07", eWithAfi.getData().get("afi").asText());

        TagsClearedPayload clearedPayload = new TagsClearedPayload("Sess1", 12346L, "Client1");
        assertEquals("rfid:tags_cleared", clearedPayload.getEvent());
        assertEquals("empty", clearedPayload.getStatus());
        assertEquals(0, clearedPayload.getTotalTags());
        assertEquals("Sess1", clearedPayload.getSession());
        assertEquals(12346L, clearedPayload.getTimestamp());
        assertEquals("Client1", clearedPayload.getClientId());
        assertTrue(clearedPayload.getSummaryMessage().contains("[READER CLEARED]"));
    }

    @Test
    @DisplayName("Test DeviceMetadataPayload model properties and summary message")
    void testDeviceMetadataPayloadModelAndSummary() {
        DeviceMetadataPayload payload = new DeviceMetadataPayload(
                "0052E688",
                "ISC.MR102-USB",
                "FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00",
                "Session-Meta",
                12345L,
                "reader-01"
        );

        assertEquals("rfid:device_metadata_response", payload.getEvent());
        assertEquals("ok", payload.getStatus());
        assertEquals("0052E688", payload.getDeviceId());
        assertEquals("ISC.MR102-USB", payload.getDeviceType());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", payload.getInitDescription());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", payload.getDescription());
        assertEquals("Session-Meta", payload.getSession());
        assertEquals(12345L, payload.getTimestamp());
        assertEquals("reader-01", payload.getClientId());

        String summary = payload.getSummaryMessage();
        assertTrue(summary.contains("[DEVICE METADATA]"));
        assertTrue(summary.contains("0052E688"));
        assertTrue(summary.contains("ISC.MR102-USB"));
    }

    @Test
    @DisplayName("Test dispatchDeviceMetadata sends DeviceMetadataPayload to all configured channels")
    void testDeviceMetadataQueryAndDispatch() {
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

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB (0052E688)"; }
            @Override
            public String getDeviceId() { return "0052E688"; }
            @Override
            public String getDeviceType() { return "ISC.MR102-USB"; }
            @Override
            public String getInitDescription() { return "FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00"; }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);
        assertSame(mockReader, dispatcher.getReader());

        // 1. Query device metadata directly
        DeviceMetadataPayload metadata = dispatcher.queryDeviceMetadata();
        assertEquals("0052E688", metadata.getDeviceId());
        assertEquals("ISC.MR102-USB", metadata.getDeviceType());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", metadata.getInitDescription());

        // 2. Dispatch to channels
        dispatcher.dispatchDeviceMetadata();

        assertEquals(2, sentEvents.size(), "Should have dispatched metadata event to both channels 'all' and 'intern'");

        Event e1 = sentEvents.get(0);
        assertEquals("all", e1.getTarget());
        assertEquals("message", e1.getType());
        assertEquals("rfid:device_metadata_response", e1.getData().get("event").asText());
        assertEquals("ok", e1.getData().get("status").asText());
        assertEquals("0052E688", e1.getData().get("deviceId").asText());
        assertEquals("ISC.MR102-USB", e1.getData().get("deviceType").asText());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", e1.getData().get("initDescription").asText());
        assertTrue(e1.getMessage().contains("[DEVICE METADATA]"));

        Event e2 = sentEvents.get(1);
        assertEquals("intern", e2.getTarget());
        assertEquals("message", e2.getType());
        assertEquals("rfid:device_metadata_response", e2.getData().get("event").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher automatically responds to incoming get_device_metadata query")
    void testDeviceMetadataAutoReply() {
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
                List.of("all"),
                null,
                mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB (0052E688)"; }
            @Override
            public String getDeviceId() { return "0052E688"; }
            @Override
            public String getDeviceType() { return "ISC.MR102-USB"; }
            @Override
            public String getInitDescription() { return "Reader initialized successfully."; }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        // Incoming message command (Go Controller HandleEvent compatible)
        Event incomingMsg = Event.createMessage("web-dashboard", "rfid-station-1", java.util.Map.of("event", "rfid:get_device_metadata"));
        incomingMsg.setToken("token-msg-999");

        dispatcher.handleIncomingMessage(incomingMsg);

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);

        assertEquals("rfid-station-1", reply.getSource());
        assertEquals("web-dashboard", reply.getTarget());
        assertEquals("message", reply.getType());
        assertEquals("token-msg-999", reply.getToken());
        assertEquals("rfid:device_metadata_response", reply.getData().get("event").asText());
        assertEquals("ok", reply.getData().get("status").asText());
        assertEquals("0052E688", reply.getData().get("deviceId").asText());
        assertEquals("ISC.MR102-USB", reply.getData().get("deviceType").asText());
        assertEquals("Reader initialized successfully.", reply.getData().get("initDescription").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher handles rfid:write_tag command with all parameters and token")
    void testWriteTagCommandSuccess() throws Exception {
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
                List.of("all"),
                null,
                mockClient
        );

        AtomicBoolean writeTagCalled = new AtomicBoolean(false);
        AtomicInteger writeAfiCalled = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals("3012345678", model.getPrimaryItemId());
                assertEquals("CH-000001-1", model.getISIL());
                assertEquals("CH", model.getCountryOfOwnerLib());
                assertEquals(2, model.getPartsInItem());
                assertEquals(1, model.getPartNumber());
                if (afi != null) {
                    assertEquals((byte) 0x07, afi.byteValue());
                    writeAfiCalled.incrementAndGet();
                }
                writeTagCalled.set(true);
            }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals((byte) 0x07, afi);
                writeAfiCalled.incrementAndGet();
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        Event writeMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "E004015099887766",
                "itemId", "3012345678",
                "isil", "CH-000001-1",
                "country", "CH",
                "parts", 2,
                "partno", 1,
                "afi", "07"
        ));
        writeMsg.setToken("req-write-123");

        dispatcher.handleIncomingMessage(writeMsg);

        // Wait for async write future to dispatch response
        Thread.sleep(400);

        assertTrue(writeTagCalled.get(), "Reader.writeTag should have been invoked");
        assertEquals(1, sentEvents.size(), "Should have received write_tag response event");

        Event reply = sentEvents.get(0);
        assertEquals("rfid-station-1", reply.getSource());
        assertEquals("web-ui", reply.getTarget());
        assertEquals("message", reply.getType());
        assertEquals("req-write-123", reply.getToken());
        assertEquals("rfid:write_tag_response", reply.getData().get("event").asText());
        assertEquals("ok", reply.getData().get("status").asText());
        assertEquals("E004015099887766", reply.getData().get("uid").asText());
        assertEquals("3012345678", reply.getData().get("itemId").asText());
        assertEquals("CH-000001-1", reply.getData().get("isil").asText());
        assertEquals("CH", reply.getData().get("country").asText());
        assertEquals(2, reply.getData().get("parts").asInt());
        assertEquals(1, reply.getData().get("partNo").asInt());
        assertEquals("07", reply.getData().get("afi").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher handles rfid:write_afi command")
    void testWriteAfiCommandSuccess() throws Exception {
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
                List.of("all"),
                null,
                mockClient
        );

        AtomicInteger writeAfiInvocations = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals("E004015099887766", uid);
                assertEquals((byte) 0xC2, afi);
                writeAfiInvocations.incrementAndGet();
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        // 1. Test rfid:write_afi with hex string
        Event afiMsg1 = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_afi",
                "uid", "E004015099887766",
                "afi", "0xC2"
        ));
        afiMsg1.setToken("req-afi-001");

        dispatcher.handleIncomingMessage(afiMsg1);
        Thread.sleep(400);

        assertEquals(1, writeAfiInvocations.get());
        assertEquals(1, sentEvents.size());

        Event reply1 = sentEvents.get(0);
        assertEquals("rfid-station-1", reply1.getSource());
        assertEquals("web-ui", reply1.getTarget());
        assertEquals("req-afi-001", reply1.getToken());
        assertEquals("rfid:write_afi_response", reply1.getData().get("event").asText());
        assertEquals("ok", reply1.getData().get("status").asText());
        assertEquals("E004015099887766", reply1.getData().get("uid").asText());
        assertEquals("C2", reply1.getData().get("afi").asText());

        // 2. Test rfid:write_afi with decimal number
        sentEvents.clear();
        Event afiMsg2 = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_afi",
                "uid", "E004015099887766",
                "afi", 194 // decimal 194 == 0xC2
        ));
        afiMsg2.setToken("req-afi-002");

        dispatcher.handleIncomingMessage(afiMsg2);
        Thread.sleep(400);

        assertEquals(2, writeAfiInvocations.get());
        assertEquals(1, sentEvents.size());

        Event reply2 = sentEvents.get(0);
        assertEquals("req-afi-002", reply2.getToken());
        assertEquals("rfid:write_afi_response", reply2.getData().get("event").asText());
        assertEquals("ok", reply2.getData().get("status").asText());
        assertEquals("C2", reply2.getData().get("afi").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test dispatcher write error reporting when reader throws exception")
    void testWriteErrorHandling() throws Exception {
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
                List.of("all"),
                null,
                mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() {}
            @Override
            public void init() {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "FEIG ISC.MR102-USB"; }
            @Override
            public void writeTag(String uid, FinnishDataModel model, Byte afi) throws Exception {
                throw new Exception("Tag write collision or transponder not in field");
            }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                throw new Exception("Tag AFI lock bit set");
            }
            @Override
            public void close() {}
        };

        dispatcher.setReader(mockReader);

        // 1. Error on writeTag
        Event writeMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "E004015099887766",
                "itemId", "3012345678"
        ));
        writeMsg.setToken("req-err-1");

        dispatcher.handleIncomingMessage(writeMsg);
        Thread.sleep(400);

        assertEquals(1, sentEvents.size());
        Event reply1 = sentEvents.get(0);
        assertEquals("req-err-1", reply1.getToken());
        assertEquals("rfid:write_tag_response", reply1.getData().get("event").asText());
        assertEquals("error", reply1.getData().get("status").asText());
        assertTrue(reply1.getData().get("error").asText().contains("Tag write collision"));

        // 2. Error on writeAfi
        sentEvents.clear();
        Event afiMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_afi",
                "uid", "E004015099887766",
                "afi", "07"
        ));
        afiMsg.setToken("req-err-2");

        dispatcher.handleIncomingMessage(afiMsg);
        Thread.sleep(400);

        assertEquals(1, sentEvents.size());
        Event reply2 = sentEvents.get(0);
        assertEquals("req-err-2", reply2.getToken());
        assertEquals("rfid:write_afi_response", reply2.getData().get("event").asText());
        assertEquals("error", reply2.getData().get("status").asText());
        assertTrue(reply2.getData().get("error").asText().contains("AFI lock bit set"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-station broadcast write_tag is executed only by the station with the active tag")
    void testBroadcastWriteTagFilteredByPresenceMultiStation() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();
        List<Event> st3Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        WSClientConfig cfg3 = new WSClientConfig();
        cfg3.setClientName("rfid-station-3");
        cfg3.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d3 = new HeadlessWsBrokerDispatcher("rfid-station-3", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg3) {
                    @Override public CompletableFuture<Void> send(Event e) { st3Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1Written = new AtomicBoolean(false);
        AtomicBoolean st2Written = new AtomicBoolean(false);
        AtomicBoolean st3Written = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st1Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st2Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d3.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-3"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st3Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);
        HeadlessReaderCallback cb3 = new HeadlessReaderCallback(null, d3, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);
        d3.setCallback(cb3);

        // Station 1 detects UID-1111
        cb1.doIt(1, 1, "NXP", "SLIX", "UID-1111", new FinnishDataModel().getBlock(32), 4);
        // Station 2 detects UID-2222
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-2222", new FinnishDataModel().getBlock(32), 4);
        // Station 3 has empty antenna field (no tags)

        st1Events.clear();
        st2Events.clear();
        st3Events.clear();

        // Broadcast write_tag command for UID-1111 published to channel 'all'
        Event broadcastEvent = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", "UID-1111",
                "itemId", "BOOK-1111",
                "isil", "CH-001",
                "country", "CH"
        ));
        broadcastEvent.setToken("bcast-tok-1");

        // Broadcast reaches all 3 stations
        d1.handleIncomingMessage(broadcastEvent);
        d2.handleIncomingMessage(broadcastEvent);
        d3.handleIncomingMessage(broadcastEvent);

        Thread.sleep(400);

        // Station 1 must have executed write and replied
        assertTrue(st1Written.get(), "Station 1 holding UID-1111 should execute writeTag");
        assertEquals(1, st1Events.size(), "Station 1 should send write_tag_response");
        assertEquals("ok", st1Events.get(0).getData().get("status").asText());
        assertEquals("bcast-tok-1", st1Events.get(0).getToken());

        // Stations 2 and 3 must silently ignore the broadcast
        assertFalse(st2Written.get(), "Station 2 (holding UID-2222) should ignore broadcast for UID-1111");
        assertEquals(0, st2Events.size(), "Station 2 should not send any response");

        assertFalse(st3Written.get(), "Station 3 (empty) should ignore broadcast for UID-1111");
        assertEquals(0, st3Events.size(), "Station 3 should not send any response");

        d1.close();
        d2.close();
        d3.close();
    }

    @Test
    @DisplayName("Test multi-station broadcast write_afi is executed only by the station with the active tag")
    void testBroadcastWriteAfiFilteredByPresence() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1AfiWritten = new AtomicBoolean(false);
        AtomicBoolean st2AfiWritten = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) {}
            @Override public void writeAfi(String u, byte a) { st1AfiWritten.set(true); }
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) {}
            @Override public void writeAfi(String u, byte a) { st2AfiWritten.set(true); }
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);

        cb1.doIt(1, 1, "NXP", "SLIX", "UID-AAAA", new FinnishDataModel().getBlock(32), 4);
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-BBBB", new FinnishDataModel().getBlock(32), 4);

        st1Events.clear();
        st2Events.clear();

        // Broadcast write_afi command for UID-BBBB
        Event broadcastAfi = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", "UID-BBBB",
                "afi", "07"
        ));
        broadcastAfi.setToken("bcast-afi-tok");

        d1.handleIncomingMessage(broadcastAfi);
        d2.handleIncomingMessage(broadcastAfi);

        Thread.sleep(400);

        assertFalse(st1AfiWritten.get(), "Station 1 should ignore write_afi for UID-BBBB");
        assertEquals(0, st1Events.size());

        assertTrue(st2AfiWritten.get(), "Station 2 holding UID-BBBB should execute write_afi");
        assertEquals(1, st2Events.size());
        assertEquals("ok", st2Events.get(0).getData().get("status").asText());
        assertEquals("UID-BBBB", st2Events.get(0).getData().get("uid").asText());

        d1.close();
        d2.close();
    }

    @Test
    @DisplayName("Test direct unicast write command for missing tag returns explicit error")
    void testDirectWriteTagNonPresentReturnsExplicitError() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean readerWriteInvoked = new AtomicBoolean(false);
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String u, FinnishDataModel m, Byte a) { readerWriteInvoked.set(true); }
            @Override public void writeAfi(String u, byte a) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        // Station has UID-PRESENT on field
        callback.doIt(1, 1, "NXP", "SLIX", "UID-PRESENT", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Direct command explicitly addressed to rfid-station-1 for missing tag UID-ABSENT
        Event directMsg = Event.createMessage("web-ui", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "UID-ABSENT",
                "itemId", "BOOK-ABSENT"
        ));
        directMsg.setToken("direct-tok-err");

        dispatcher.handleIncomingMessage(directMsg);
        Thread.sleep(200);

        assertFalse(readerWriteInvoked.get(), "Reader hardware should not be touched for absent tag in direct mode");
        assertEquals(1, sentEvents.size(), "Direct message should receive explicit error response");

        Event reply = sentEvents.get(0);
        assertEquals("rfid:write_tag_response", reply.getData().get("event").asText());
        assertEquals("error", reply.getData().get("status").asText());
        assertTrue(reply.getData().get("error").asText().contains("not present on reader station"));
        assertEquals("direct-tok-err", reply.getToken());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test wildcard broadcast write_tag executes when exactly 1 tag is present, ignores when ambiguous")
    void testWildcardBroadcastWriteTagSingleVsMultipleTags() throws Exception {
        List<Event> st1Events = new CopyOnWriteArrayList<>();
        List<Event> st2Events = new CopyOnWriteArrayList<>();

        WSClientConfig cfg1 = new WSClientConfig();
        cfg1.setClientName("rfid-station-1");
        cfg1.setEndpoint("https://localhost:8765");

        WSClientConfig cfg2 = new WSClientConfig();
        cfg2.setClientName("rfid-station-2");
        cfg2.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher d1 = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg1) {
                    @Override public CompletableFuture<Void> send(Event e) { st1Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        HeadlessWsBrokerDispatcher d2 = new HeadlessWsBrokerDispatcher("rfid-station-2", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg2) {
                    @Override public CompletableFuture<Void> send(Event e) { st2Events.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean st1Written = new AtomicBoolean(false);
        AtomicBoolean st2Written = new AtomicBoolean(false);

        d1.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) {
                assertEquals("UID-SOLO", uid);
                st1Written.set(true);
            }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        d2.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-2"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { st2Written.set(true); }
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback cb1 = new HeadlessReaderCallback(null, d1, 3000L, 2000L);
        HeadlessReaderCallback cb2 = new HeadlessReaderCallback(null, d2, 3000L, 2000L);

        d1.setCallback(cb1);
        d2.setCallback(cb2);

        // Station 1: exactly 1 tag
        cb1.doIt(1, 1, "NXP", "SLIX", "UID-SOLO", new FinnishDataModel().getBlock(32), 4);

        // Station 2: multiple (2) tags
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-MULTI-1", new FinnishDataModel().getBlock(32), 4);
        cb2.doIt(1, 1, "NXP", "SLIX", "UID-MULTI-2", new FinnishDataModel().getBlock(32), 4);

        st1Events.clear();
        st2Events.clear();

        // Broadcast wildcard write command (empty uid)
        Event wildcardEvent = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", "",
                "itemId", "WILDCARD-ITEM"
        ));
        wildcardEvent.setToken("wildcard-tok");

        d1.handleIncomingMessage(wildcardEvent);
        d2.handleIncomingMessage(wildcardEvent);

        Thread.sleep(400);

        assertTrue(st1Written.get(), "Station 1 with single tag should execute wildcard write");
        assertEquals(1, st1Events.size());
        assertEquals("UID-SOLO", st1Events.get(0).getData().get("uid").asText());

        assertFalse(st2Written.get(), "Station 2 with multiple tags should ignore wildcard write");
        assertEquals(0, st2Events.size());

        d1.close();
        d2.close();
    }

    @Test
    @DisplayName("Test back-to-back chained write operations succeed without waiting for scan loop")
    void testBackToBackChainedWriteOperations() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        AtomicBoolean writeTagDone = new AtomicBoolean(false);
        AtomicBoolean writeAfiDone = new AtomicBoolean(false);

        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) { writeTagDone.set(true); }
            @Override public void writeAfi(String uid, byte afi) { writeAfiDone.set(true); }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        String uid = "UID-CHAIN-123";
        callback.doIt(1, 1, "NXP", "SLIX", uid, new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // 1. First broadcast write_tag
        Event writeTagEvt = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", uid,
                "itemId", "CHAIN-ITEM"
        ));
        writeTagEvt.setToken("chain-tok-1");
        dispatcher.handleIncomingMessage(writeTagEvt);

        Thread.sleep(400);
        assertTrue(writeTagDone.get(), "First write_tag should succeed");
        assertEquals(1, sentEvents.size());

        // 2. Immediate second broadcast write_afi for same tag without waiting for background scan
        Event writeAfiEvt = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", uid,
                "afi", "07"
        ));
        writeAfiEvt.setToken("chain-tok-2");
        dispatcher.handleIncomingMessage(writeAfiEvt);

        Thread.sleep(400);
        assertTrue(writeAfiDone.get(), "Chained write_afi should immediately succeed on same tag");
        assertEquals(2, sentEvents.size());
        assertEquals("ok", sentEvents.get(1).getData().get("status").asText());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test RefID correlation across metadata queries, tag write, AFI write and error replies")
    void testRefIdCorrelationInDispatcher() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();

        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("rfid-station-1", "https://localhost:8765", List.of("all"), null,
                new WSBrokerClient(cfg) {
                    @Override public CompletableFuture<Void> send(Event e) { sentEvents.add(e); return CompletableFuture.completedFuture(null); }
                });

        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceId() { return "DEV-999"; }
            @Override public String getDeviceType() { return "OBID-LRU1002"; }
            @Override public String getInitDescription() { return "Feig LRU1002 Initialized"; }
            @Override public String getDeviceInfo() { return "Reader-1"; }
            @Override public void writeTag(String uid, FinnishDataModel m, Byte afi) {}
            @Override public void writeAfi(String uid, byte afi) {}
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        String tagUid = "UID-CORRELATION-TEST";
        callback.doIt(1, 1, "NXP", "SLIX", tagUid, new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // 1. Device metadata command with specific ID
        Event metaCmd = Event.createMessage("dashboard", "rfid-station-1", Map.of("event", "rfid:get_device_metadata"));
        metaCmd.setId("meta-req-id-11111111111111111111");
        metaCmd.setToken("meta-tok");
        dispatcher.handleIncomingMessage(metaCmd);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event metaResp = sentEvents.get(0);
        assertEquals("meta-req-id-11111111111111111111", metaResp.getRefId());
        assertEquals("meta-tok", metaResp.getToken());
        assertEquals("rfid:device_metadata_response", metaResp.getData().get("event").asText());
        assertNotNull(metaResp.getId());

        // 2. Write tag command with specific ID
        sentEvents.clear();
        Event writeTagCmd = Event.createMessage("dashboard", "all", Map.of(
                "event", "rfid:write_tag",
                "uid", tagUid,
                "itemId", "ITEM-REFID-1"
        ));
        writeTagCmd.setId("write-tag-id-2222222222222222");
        writeTagCmd.setToken("write-tag-tok");
        dispatcher.handleIncomingMessage(writeTagCmd);

        Thread.sleep(300);
        assertEquals(1, sentEvents.size());
        Event writeTagResp = sentEvents.get(0);
        assertEquals("write-tag-id-2222222222222222", writeTagResp.getRefId());
        assertEquals("write-tag-tok", writeTagResp.getToken());
        assertEquals("rfid:write_tag_response", writeTagResp.getData().get("event").asText());
        assertEquals("ok", writeTagResp.getData().get("status").asText());

        // 3. Write AFI command with specific ID
        sentEvents.clear();
        Event writeAfiCmd = Event.createMessage("dashboard", "all", Map.of(
                "event", "rfid:write_afi",
                "uid", tagUid,
                "afi", "07"
        ));
        writeAfiCmd.setId("write-afi-id-3333333333333333");
        writeAfiCmd.setToken("write-afi-tok");
        dispatcher.handleIncomingMessage(writeAfiCmd);

        Thread.sleep(300);
        assertEquals(1, sentEvents.size());
        Event writeAfiResp = sentEvents.get(0);
        assertEquals("write-afi-id-3333333333333333", writeAfiResp.getRefId());
        assertEquals("write-afi-tok", writeAfiResp.getToken());
        assertEquals("rfid:write_afi_response", writeAfiResp.getData().get("event").asText());
        assertEquals("ok", writeAfiResp.getData().get("status").asText());

        // 4. Direct write command error reply when tag not present
        sentEvents.clear();
        Event directMissingWrite = Event.createMessage("dashboard", "rfid-station-1", Map.of(
                "event", "rfid:write_tag",
                "uid", "NON-EXISTENT-UID",
                "itemId", "ITEM-FAIL"
        ));
        directMissingWrite.setId("error-req-id-44444444444444");
        directMissingWrite.setToken("err-tok");
        dispatcher.handleIncomingMessage(directMissingWrite);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event errResp = sentEvents.get(0);
        assertEquals("error-req-id-44444444444444", errResp.getRefId());
        assertEquals("err-tok", errResp.getToken());
        assertEquals("error", errResp.getData().get("status").asText());

        // 5. handleMetadataQuery via CLIENT_INFO / non-message query
        sentEvents.clear();
        Event infoQuery = Event.builder()
                .id("info-query-id-55555555555555")
                .source("external-service")
                .target("rfid-station-1")
                .type(org.objectspace.wsbrokerclient.event.EventType.CLIENT_INFO)
                .token("info-tok")
                .build();
        dispatcher.handleMetadataQuery(infoQuery);

        Thread.sleep(200);
        assertEquals(1, sentEvents.size());
        Event infoResp = sentEvents.get(0);
        assertEquals("info-query-id-55555555555555", infoResp.getRefId());
        assertEquals("info-tok", infoResp.getToken());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI when all transponders succeed")
    void testBatchWriteAfiAllSuccess() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> writtenUids = new CopyOnWriteArrayList<>();
        AtomicInteger exclusiveLocks = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                assertEquals((byte) 0x07, afi);
                writtenUids.add(uid);
            }
            @Override public void close() {}
        };

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, null, 100, 4) {
            @Override
            public <T> T executeExclusive(ScannerTask<T> task) throws Exception {
                exclusiveLocks.incrementAndGet();
                return super.executeExclusive(task);
            }
        };

        dispatcher.setReader(mockReader);
        dispatcher.setScanner(scanner);

        List<String> requestedUids = List.of("E004010001", "E004010002", "E004010003");
        WriteAfiResponsePayload resp = dispatcher.writeAfi(requestedUids, "07", "web-ui", "req-batch-all-ok", "ref-batch-001").join();

        assertEquals("ok", resp.getStatus());
        assertTrue(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertEquals("07", resp.getAfi());
        assertEquals(requestedUids, resp.getUids());
        assertEquals(requestedUids, resp.getSuccessfulUids());
        assertTrue(resp.getFailedUids() == null || resp.getFailedUids().isEmpty());
        assertEquals(3, resp.getResults().size());
        assertTrue(resp.getResults().stream().allMatch(WriteAfiResponsePayload.TagAfiResult::isSuccess));
        assertEquals(3, writtenUids.size());
        assertEquals(1, exclusiveLocks.get(), "Batch write should acquire exclusive scanner lock exactly once for all UIDs");

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("req-batch-all-ok", reply.getToken());
        assertEquals("ref-batch-001", reply.getRefId());
        assertEquals("rfid:write_afi_response", reply.getData().get("event").asText());
        assertEquals("ok", reply.getData().get("status").asText());
        assertEquals(3, reply.getData().get("uids").size());
        assertEquals(3, reply.getData().get("successfulUids").size());

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI with partial failure reports exact successes and errors")
    void testBatchWriteAfiPartialFailure() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                if ("E00401-LOCKED".equals(uid)) {
                    throw new Exception("Tag AFI lock bit set (ISO error 0x12)");
                }
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<String> requestedUids = List.of("E00401-OK1", "E00401-LOCKED", "E00401-OK2");
        WriteAfiResponsePayload resp = dispatcher.writeAfi(requestedUids, "0xC2", "web-ui", "req-batch-partial", "ref-partial-002").join();

        assertEquals("partial", resp.getStatus());
        assertFalse(resp.isSuccess());
        assertTrue(resp.isPartial());
        assertEquals("C2", resp.getAfi());
        assertEquals(requestedUids, resp.getUids());
        assertEquals(List.of("E00401-OK1", "E00401-OK2"), resp.getSuccessfulUids());
        assertNotNull(resp.getFailedUids());
        assertEquals(1, resp.getFailedUids().size());
        assertTrue(resp.getFailedUids().get("E00401-LOCKED").contains("Tag AFI lock bit set"));

        assertEquals(3, resp.getResults().size());
        assertTrue(resp.getResults().get(0).isSuccess());
        assertFalse(resp.getResults().get(1).isSuccess());
        assertEquals("E00401-LOCKED", resp.getResults().get(1).getUid());
        assertTrue(resp.getResults().get(1).getError().contains("Tag AFI lock bit set"));
        assertTrue(resp.getResults().get(2).isSuccess());

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("partial", reply.getData().get("status").asText());
        assertEquals(2, reply.getData().get("successfulUids").size());
        assertTrue(reply.getData().get("failedUids").has("E00401-LOCKED"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test multi-UID batch write AFI when all transponders fail")
    void testBatchWriteAfiAllFailure() throws Exception {
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, new WSBrokerClient(cfg)
        );

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader-Batch"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                throw new Exception("Tag not in field");
            }
            @Override public void close() {}
        };

        dispatcher.setReader(mockReader);

        List<String> requestedUids = List.of("UID-FAIL-1", "UID-FAIL-2");
        WriteAfiResponsePayload resp = dispatcher.writeAfi(requestedUids, "07", null, null).join();

        assertEquals("error", resp.getStatus());
        assertFalse(resp.isSuccess());
        assertFalse(resp.isPartial());
        assertTrue(resp.getSuccessfulUids().isEmpty());
        assertEquals(2, resp.getFailedUids().size());
        assertTrue(resp.getFailedUids().get("UID-FAIL-1").contains("Tag not in field"));
        assertTrue(resp.getFailedUids().get("UID-FAIL-2").contains("Tag not in field"));

        dispatcher.close();
    }

    @Test
    @DisplayName("Test incoming rfid:write_afi message with uids array triggers batch processing")
    void testIncomingMessageWithUidsArray() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        WSClientConfig cfg = new WSClientConfig();
        cfg.setClientName("rfid-station-1");
        cfg.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(cfg) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "rfid-station-1", "https://localhost:8765", List.of("all"), null, mockClient
        );

        List<String> written = new CopyOnWriteArrayList<>();
        dispatcher.setReader(new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback cb, int n) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Reader"; }
            @Override
            public void writeAfi(String uid, byte afi) throws Exception {
                written.add(uid);
            }
            @Override public void close() {}
        });

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 3000L, 2000L);
        dispatcher.setCallback(callback);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-1", new FinnishDataModel().getBlock(32), 4);
        callback.doIt(1, 1, "NXP", "SLIX", "E00401-TAG-2", new FinnishDataModel().getBlock(32), 4);
        sentEvents.clear();

        // Broadcast message with uids array
        Event batchMsg = Event.createMessage("web-ui", "all", Map.of(
                "event", "rfid:write_afi",
                "uids", List.of("E00401-TAG-1", "E00401-TAG-2"),
                "afi", "C2"
        ));
        batchMsg.setToken("req-batch-array");

        dispatcher.handleIncomingMessage(batchMsg);
        Thread.sleep(400);

        assertEquals(2, written.size());
        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);
        assertEquals("req-batch-array", reply.getToken());
        assertEquals("ok", reply.getData().get("status").asText());
        assertEquals("C2", reply.getData().get("afi").asText());
        assertEquals(2, reply.getData().get("successfulUids").size());

        dispatcher.close();
    }
}

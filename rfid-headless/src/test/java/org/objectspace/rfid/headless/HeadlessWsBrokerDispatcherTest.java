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
        assertTrue(tagPayload.getSummaryMessage().contains("UID: UID-1"));

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
}

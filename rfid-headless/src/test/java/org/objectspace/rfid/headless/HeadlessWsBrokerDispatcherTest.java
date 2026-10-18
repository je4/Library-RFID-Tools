package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.core.TagScanProcessor;
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
        assertEquals("ITEM-42", wsEvents.get(0).getData().get("itemId").asText());
        assertEquals("UID-42", wsEvents.get(0).getData().get("uid").asText());

        callback.close();
    }
}

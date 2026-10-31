package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.headless.payload.DeviceMetadataData;
import org.objectspace.rfid.library.ISO15693Reader;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class WsBrokerDeviceMetadataTest {

    private static JsonNode payloadData(Event<?> evt) {
        return evt.getDataNode();
    }

    @Test
    @DisplayName("Test dispatchDeviceMetadata sends DeviceMetadataData to all configured channels")
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
        DeviceMetadataData metadata = dispatcher.queryDeviceMetadata();
        assertEquals("0052E688", metadata.deviceId());
        assertEquals("ISC.MR102-USB", metadata.deviceType());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", metadata.initDescription());

        // 2. Dispatch to channels
        dispatcher.dispatchDeviceMetadata();

        assertEquals(2, sentEvents.size(), "Should have dispatched metadata event to both channels 'all' and 'intern'");

        Event e1 = sentEvents.get(0);
        assertEquals("all", e1.getTarget());
        assertEquals("rfid/device_metadata_response", e1.getType());
        assertEquals("ok", payloadData(e1).get("status").asText());
        assertEquals("0052E688", payloadData(e1).get("deviceId").asText());
        assertEquals("ISC.MR102-USB", payloadData(e1).get("deviceType").asText());
        assertEquals("FEIG Electronic ISC.MR102-USB (0052E688)\nFirmware: V03.01.00", payloadData(e1).get("initDescription").asText());
        assertTrue(e1.getMessage().contains("[DEVICE METADATA]"));

        Event e2 = sentEvents.get(1);
        assertEquals("intern", e2.getTarget());
        assertEquals("rfid/device_metadata_response", e2.getType());

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

        // Incoming command with direct custom event type
        Event incomingMsg = Event.of("rfid/get_device_metadata", "web-dashboard", "rfid-station-1", null);

        dispatcher.handleIncomingMessage(incomingMsg);

        assertEquals(1, sentEvents.size());
        Event reply = sentEvents.get(0);

        assertEquals("rfid-station-1", reply.getSource());
        assertEquals("web-dashboard", reply.getTarget());
        assertEquals("rfid/device_metadata_response", reply.getType());
        assertEquals("ok", payloadData(reply).get("status").asText());
        assertEquals("0052E688", payloadData(reply).get("deviceId").asText());
        assertEquals("ISC.MR102-USB", reply.getDataNode().get("deviceType").asText());
        assertEquals("Reader initialized successfully.", payloadData(reply).get("initDescription").asText());

        dispatcher.close();
    }
}

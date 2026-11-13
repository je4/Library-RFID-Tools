package org.objectspace.rfid.headless;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.headless.payload.TagReadData;
import org.objectspace.wsbrokerclient.client.ConnectionListener;
import org.objectspace.wsbrokerclient.client.ConnectionState;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.ntp.NTPPacket;
import org.objectspace.wsbrokerclient.ntp.NTPResponse;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying time handling in {@link TagReadData} across live reader scans,
 * buffered gate events, WSBroker dispatching, and JSON serialization.
 *
 * <p>Validates whether {@code TagReadData.time} always contains the current time:
 * <ul>
 *   <li>Live / Real-time scans: {@code time} is automatically populated with the current UTC timestamp (adjusted with NTP offset if present).</li>
 *   <li>Buffered / Historical scans (e.g. from FEIG Gate buffer): {@code time} preserves the original historical event timestamp and is NOT overwritten with the current clock time.</li>
 * </ul>
 */
public class TagReadDataTimeIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HeadlessWsBrokerDispatcher.registerPayloads();
    }

    @Test
    @DisplayName("Live reader scan automatically populates TagReadData.time with the current UTC timestamp")
    public void testLiveScanGeneratesCurrentUtcTime() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        HeadlessWsBrokerDispatcher dispatcher = createMockDispatcher("test-station", "rfid:live", sentEvents);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "station-01");

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-LIVE-001", "CH", "ISIL-001", new ArrayList<>());
        byte[] blockData = model.getBlock(32);

        Instant beforeScan = Instant.now().minusSeconds(1);

        // Live scan without explicit eventTime
        callback.doIt(1, 1, "NXP", "SLIX", "E004010000000001", blockData, 4);

        Instant afterScan = Instant.now().plusSeconds(2);

        assertEquals(1, sentEvents.size(), "Expected 1 event to be dispatched for live scan");
        Event<?> event = sentEvents.get(0);
        assertEquals(TagReadData.EVENT_NAME, event.getType());

        assertTrue(event.getData() instanceof TagReadData);
        TagReadData tagData = (TagReadData) event.getData();

        assertEquals("E004010000000001", tagData.uid());
        assertEquals("BOOK-LIVE-001", tagData.itemId());
        assertEquals("station-01", tagData.readerId());

        // Assert that time is set and corresponds to current UTC time
        assertNotNull(tagData.time(), "TagReadData.time must not be null for live scans");
        assertFalse(tagData.time().isBlank(), "TagReadData.time must not be blank for live scans");

        Instant scanTime = Instant.parse(tagData.time());
        assertTrue(!scanTime.isBefore(beforeScan) && !scanTime.isAfter(afterScan),
                "TagReadData.time (" + tagData.time() + ") must be within current scan time window ["
                        + beforeScan + " - " + afterScan + "]");

        dispatcher.close();
    }

    @Test
    @DisplayName("Buffered gate scan preserves historical event timestamp and does NOT overwrite with current time")
    public void testBufferedGateScanPreservesHistoricalTimestamp() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        HeadlessWsBrokerDispatcher dispatcher = createMockDispatcher("gate-client", "rfid:gate-north", sentEvents);

        MockGateReader mockReader = new MockGateReader();
        mockReader.setConnected(true);

        // Historical timestamp from 2 hours in the past
        String historicalTime = "2026-11-13T08:15:30.500";

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "HIST-001", "CH", "ISIL-001", new ArrayList<>());
        byte[] blockData = model.getBlock(32);

        mockReader.addBufferedTag("E004019999888877", blockData, 4, historicalTime);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "gate-north");
        HeadlessGateWorker gateWorker = new HeadlessGateWorker(mockReader, callback, dispatcher, 50, 1000, false, "gate-north");

        gateWorker.start();

        long deadline = System.currentTimeMillis() + 2000;
        while (sentEvents.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        gateWorker.stop();

        assertEquals(1, sentEvents.size(), "Expected 1 event to be dispatched from gate worker");
        Event<?> event = sentEvents.get(0);
        TagReadData tagData = (TagReadData) event.getData();

        assertEquals("E004019999888877", tagData.uid());
        assertEquals("HIST-001", tagData.itemId());
        assertEquals("gate-north", tagData.readerId());

        // Key verification: time must be the historical timestamp, NOT the current time!
        assertEquals(historicalTime, tagData.time(),
                "TagReadData.time must preserve historical timestamp from gate buffer and NOT be overwritten by current time");

        dispatcher.close();
    }

    @Test
    @DisplayName("Explicitly supplied TagReadData withTime is preserved during dispatching and JSON serialization")
    public void testDirectDispatcherPreservesExplicitTimestamp() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        HeadlessWsBrokerDispatcher dispatcher = createMockDispatcher("test-station", "rfid:tags", sentEvents);

        String customTimestamp = "2025-06-15T14:30:00Z";
        TagReadData originalData = new TagReadData(
                "E004015011223344",
                "BOOK-EXPLICIT",
                "01020304",
                "ISIL-TEST",
                "CH",
                true,
                "SLIX",
                1,
                1,
                1,
                1,
                "07",
                "manual-reader"
        ).withTime(customTimestamp);

        dispatcher.dispatchTag(originalData, "session-123", System.currentTimeMillis());

        assertEquals(1, sentEvents.size());
        Event<?> event = sentEvents.get(0);
        TagReadData dispatchedData = (TagReadData) event.getData();

        assertEquals(customTimestamp, dispatchedData.time(), "Dispatched TagReadData must keep the explicit time");

        // Verify JSON serialization via WSBroker Jackson mapping
        String json = objectMapper.writeValueAsString(dispatchedData);
        JsonNode jsonNode = objectMapper.readTree(json);
        assertEquals(customTimestamp, jsonNode.get("time").asText(), "Serialized JSON must contain explicit time");

        // Verify deserialization back to TagReadData
        TagReadData deserialized = objectMapper.treeToValue(jsonNode, TagReadData.class);
        assertEquals(customTimestamp, deserialized.time(), "Deserialized TagReadData must retain explicit time");

        dispatcher.close();
    }

    @Test
    @DisplayName("Buffered tag without timestamp falls back to generating current UTC timestamp")
    public void testBufferedTagWithoutTimeFallsBackToCurrentTime() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        HeadlessWsBrokerDispatcher dispatcher = createMockDispatcher("gate-client", "rfid:gate-north", sentEvents);

        MockGateReader mockReader = new MockGateReader();
        mockReader.setConnected(true);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "FALLBACK-001", "CH", "ISIL-001", new ArrayList<>());
        byte[] blockData = model.getBlock(32);

        // Add buffered tag with null timestamp
        mockReader.addBufferedTag("E004015555444433", blockData, 4, null);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "gate-north");
        HeadlessGateWorker gateWorker = new HeadlessGateWorker(mockReader, callback, dispatcher, 50, 1000, false, "gate-north");

        Instant beforeScan = Instant.now().minusSeconds(1);

        gateWorker.start();

        long deadline = System.currentTimeMillis() + 2000;
        while (sentEvents.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        gateWorker.stop();

        Instant afterScan = Instant.now().plusSeconds(2);

        assertEquals(1, sentEvents.size());
        TagReadData tagData = (TagReadData) sentEvents.get(0).getData();

        assertEquals("E004015555444433", tagData.uid());
        assertEquals("FALLBACK-001", tagData.itemId());
        assertNotNull(tagData.time(), "TagReadData.time must not be null when fallback is applied");

        Instant parsedTime = Instant.parse(tagData.time());
        assertTrue(!parsedTime.isBefore(beforeScan) && !parsedTime.isAfter(afterScan),
                "Fallback timestamp must be within current execution window");

        dispatcher.close();
    }

    @Test
    @DisplayName("Live scan time incorporates WSBroker NTP offset while preserving explicit historical time")
    public void testTimeWithNtpOffsetCorrection() throws Exception {
        List<Event> sentEvents = new CopyOnWriteArrayList<>();
        Instant localTime = Instant.parse("2026-11-13T12:00:00Z");

        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName("ntp-client");
        clientConfig.setEndpoint("https://localhost:8765");

        ConnectionListener[] listenerHolder = new ConnectionListener[1];
        CompletableFuture<NTPResponse> ntpFuture = new CompletableFuture<>();

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                sentEvents.add(event);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void addConnectionListener(ConnectionListener listener) {
                listenerHolder[0] = listener;
            }

            @Override
            public CompletableFuture<NTPResponse> queryNTP(Duration timeout) {
                return ntpFuture;
            }
        };

        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher(
                "ntp-client", "https://localhost:8765", List.of("rfid"), null, mockClient);

        // Trigger connection event
        listenerHolder[0].onStateChanged(ConnectionState.CONNECTING, ConnectionState.CONNECTED);

        // Simulate NTP response with +5 seconds offset
        NTPPacket packet = new NTPPacket().setStratum(1)
                .setOriginateTimestamp(localTime)
                .setReceiveTimestamp(localTime.plusSeconds(5))
                .setTransmitTimestamp(localTime.plusSeconds(5));
        ntpFuture.complete(new NTPResponse(packet, localTime));

        // 1. Live scan without explicit eventTime: time should be adjusted by +5 seconds
        dispatcher.dispatchTag("UID-LIVE", "ITEM-LIVE", "0102", "ISIL", "CH", true, "SLIX",
                null, localTime.toEpochMilli(), "ntp-client", "reader-1", null, "07", null);

        // 2. Historical scan with explicit eventTime: time should remain untouched
        String historicalEventTime = "2026-11-13T09:00:00";
        dispatcher.dispatchTag("UID-HIST", "ITEM-HIST", "0102", "ISIL", "CH", true, "SLIX",
                null, localTime.toEpochMilli(), "ntp-client", "reader-1", null, "07", historicalEventTime);

        assertEquals(2, sentEvents.size());

        TagReadData liveData = (TagReadData) sentEvents.get(0).getData();
        assertEquals(localTime.plusSeconds(5).toString(), liveData.time(),
                "Live scan time must be corrected with NTP offset (+5s)");

        TagReadData histData = (TagReadData) sentEvents.get(1).getData();
        assertEquals(historicalEventTime, histData.time(),
                "Explicit event time must NOT be modified by NTP offset or replaced by current clock time");

        dispatcher.close();
    }

    private HeadlessWsBrokerDispatcher createMockDispatcher(String clientName, String channel, List<Event> eventList) {
        WSClientConfig clientConfig = new WSClientConfig();
        clientConfig.setClientName(clientName);
        clientConfig.setEndpoint("https://localhost:8765");

        WSBrokerClient mockClient = new WSBrokerClient(clientConfig) {
            @Override
            public CompletableFuture<Void> send(Event event) {
                eventList.add(event);
                return CompletableFuture.completedFuture(null);
            }
        };

        return new HeadlessWsBrokerDispatcher(clientName, "https://localhost:8765", List.of(channel), null, mockClient);
    }

    private static class MockGateReader extends MockISO15693Reader
            implements HeadlessGateWorker.BufferedTagReader, HeadlessGateWorker.GateStatusReader, HeadlessGateWorker.BufferedModeConfigurator {

        private final List<FeigRFID.BufferedTag> bufferedQueue = new CopyOnWriteArrayList<>();

        void addBufferedTag(String uid, byte[] data, long blockSize, String dateTime) {
            bufferedQueue.add(new FeigRFID.BufferedTag(uid, data, blockSize, dateTime, 1));
        }

        @Override
        public void configureBufferedMode(File configFile) {
        }

        @Override
        public List<FeigRFID.BufferedTag> readBufferedTags(int maxCount) {
            if (bufferedQueue.isEmpty()) {
                return Collections.emptyList();
            }
            List<FeigRFID.BufferedTag> result = new ArrayList<>();
            for (int i = 0; i < maxCount && !bufferedQueue.isEmpty(); i++) {
                result.add(bufferedQueue.remove(0));
            }
            return result;
        }

        @Override
        public List<FeigRFID.GateCounter> readGateCounters() {
            return Collections.emptyList();
        }

        @Override
        public List<FeigRFID.GateDiagnostic> readGateDiagnostics() {
            return Collections.emptyList();
        }
    }
}

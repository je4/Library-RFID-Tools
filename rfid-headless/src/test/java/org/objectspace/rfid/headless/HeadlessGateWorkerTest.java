package org.objectspace.rfid.headless;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponseData;
import org.objectspace.rfid.headless.payload.WriteTagResponseData;
import org.objectspace.wsbrokerclient.event.Event;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessGateWorkerTest {

    static class MockGateReader extends MockISO15693Reader
            implements HeadlessGateWorker.BufferedTagReader, HeadlessGateWorker.GateStatusReader,
            HeadlessGateWorker.BufferedModeConfigurator {

        private final List<FeigRFID.BufferedTag> bufferedQueue = new CopyOnWriteArrayList<>();
        private final List<String> callingThreads = new CopyOnWriteArrayList<>();
        private final AtomicInteger connectCalls = new AtomicInteger(0);
        private final AtomicInteger closeCalls = new AtomicInteger(0);
        private final AtomicInteger configCalls = new AtomicInteger(0);
        private volatile File configuredFile;

        private List<FeigRFID.GateCounter> counters = List.of(
                new FeigRFID.GateCounter(new FeigRFID.GateAddress(1, 1), 10, 20, 30, 40)
        );
        private final List<FeigRFID.GateDiagnostic> diagnostics = List.of(
                new FeigRFID.GateDiagnostic(new FeigRFID.GateAddress(1, 1), 0, 1)
        );
        private final AtomicBoolean bufferedMode = new AtomicBoolean(true);
        private final AtomicBoolean hostMode = new AtomicBoolean(false);
        private final AtomicInteger operatingModeChecks = new AtomicInteger(0);

        public void addBufferedTag(String uid, byte[] data, long blockSize) {
            bufferedQueue.add(new FeigRFID.BufferedTag(uid, data, blockSize, "2026-11-13T11:00:00", 1));
        }

        @Override
        public boolean isBufferedMode() {
            operatingModeChecks.incrementAndGet();
            return bufferedMode.get();
        }

        @Override
        public boolean isHostMode() {
            return hostMode.get();
        }

        public void setBufferedModeState(boolean isBuffered) {
            this.bufferedMode.set(isBuffered);
            this.hostMode.set(!isBuffered);
        }

        @Override
        public void connect() throws Exception {
            callingThreads.add("connect:" + Thread.currentThread().getName());
            connectCalls.incrementAndGet();
            setConnected(true);
        }

        @Override
        public void close() throws Exception {
            callingThreads.add("close:" + Thread.currentThread().getName());
            closeCalls.incrementAndGet();
            setConnected(false);
        }

        @Override
        public void configureBufferedMode(File configFile) {
            callingThreads.add("configureBufferedMode:" + Thread.currentThread().getName());
            configuredFile = configFile;
            configCalls.incrementAndGet();
        }

        @Override
        public List<FeigRFID.BufferedTag> readBufferedTags(int maxCount) throws Exception {
            callingThreads.add("readBufferedTags:" + Thread.currentThread().getName());
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
        public List<FeigRFID.GateCounter> readGateCounters() throws Exception {
            callingThreads.add("readGateCounters:" + Thread.currentThread().getName());
            return counters;
        }

        @Override
        public List<FeigRFID.GateDiagnostic> readGateDiagnostics() throws Exception {
            callingThreads.add("readGateDiagnostics:" + Thread.currentThread().getName());
            return diagnostics;
        }
    }

    @Test
    @DisplayName("Gate worker executes all SDK interactions on a single dedicated worker thread")
    public void workerRunsOnDedicatedThreadWithAffinity() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(false);

        HeadlessReaderConfig cfg = new HeadlessReaderConfig("gate-01");
        cfg.setOperatingMode("buffered");
        cfg.setBufferPollInterval(50);
        cfg.setGatePollInterval(100);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, null, 1000L, "gate-01");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, null, 50, 100, true, "gate-01");

        worker.start();
        Thread.sleep(250);

        // Execute task via executeExclusive and record execution thread
        AtomicReference<String> exclusiveThread = new AtomicReference<>();
        worker.executeExclusive(r -> {
            exclusiveThread.set(Thread.currentThread().getName());
            return null;
        });

        worker.stop();

        assertEquals(0, reader.configCalls.get());
        assertNotNull(exclusiveThread.get());
        assertEquals("RFID-GateWorker-gate-01", exclusiveThread.get());

        // Verify all SDK calls were performed on RFID-GateWorker-gate-01
        assertFalse(reader.callingThreads.isEmpty());
        for (String entry : reader.callingThreads) {
            assertTrue(entry.endsWith("RFID-GateWorker-gate-01"),
                    "SDK call was on unexpected thread: " + entry);
        }
    }

    @Test
    public void autoConfigAppliesBufferedFileOnceOnWorkerThread(@TempDir Path tempDir) throws Exception {
        File configFile = Files.createFile(tempDir.resolve("buffered.xml")).toFile();
        MockGateReader reader = new MockGateReader();
        reader.setConnected(false);
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, null, null, 20, 1000,
                false, "gate-config", true, configFile);

        worker.start();
        long deadline = System.currentTimeMillis() + 2000;
        while (reader.configCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        worker.stop();

        assertEquals(1, reader.configCalls.get());
        assertEquals(configFile, reader.configuredFile);
        assertTrue(reader.callingThreads.contains("configureBufferedMode:RFID-GateWorker-gate-config"));
    }

    @Test
    public void autoConfigFailureStopsWorkerBeforePolling(@TempDir Path tempDir) throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(false);
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, null, null, 20, 1000,
                false, "gate-missing-config", true, tempDir.resolve("missing.xml").toFile());

        worker.start();
        long deadline = System.currentTimeMillis() + 2000;
        while (worker.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        worker.stop();

        assertFalse(worker.isRunning());
        assertEquals(0, reader.configCalls.get());
        assertTrue(reader.callingThreads.stream().noneMatch(entry -> entry.startsWith("readBufferedTags:")));
    }

    @Test
    @DisplayName("Gate worker polls buffered tags and dispatches them via callback and WSBroker")
    public void workerPollsAndDispatchesBufferedTags() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(true);

        // Prepare test tag with FinnishDataModel metadata
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-12345", "CH", "ISIL-123", null);
        byte[] blockData = model.getBlock(32);
        reader.addBufferedTag("E004010000000001", blockData, 4);

        TestWsBrokerClient testClient = new TestWsBrokerClient("test-client");
        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("test-client", "https://localhost:8765", List.of("rfid:gate-01"), null, testClient, true);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "gate-01");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, dispatcher, 50, 1000, false, "gate-01");

        worker.start();

        // Wait for tag to be polled and dispatched
        long deadline = System.currentTimeMillis() + 2000;
        while (testClient.getSentEvents().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        worker.stop();

        assertFalse(testClient.getSentEvents().isEmpty(), "Expected tag read event to be dispatched");
        Event<?> event = testClient.getSentEvents().get(0);
        assertEquals("rfid/tag_read", event.getType());
        assertEquals("test-client", event.getSource());

        // Verify dispatched payload content
        assertTrue(event.getData() instanceof org.objectspace.rfid.headless.payload.TagReadData);
        org.objectspace.rfid.headless.payload.TagReadData data = (org.objectspace.rfid.headless.payload.TagReadData) event.getData();
        assertEquals("E004010000000001", data.uid());
        assertEquals("BOOK-12345", data.itemId());
        assertEquals("gate-01", data.readerId());
        assertEquals("2026-11-13T11:00:00", data.time());
    }

    @Test
    @DisplayName("Gate worker polls buffered tag without timestamp and falls back to generated time")
    public void workerPollsAndDispatchesBufferedTagWithoutTimeFallback() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(true);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-FALLBACK", "CH", "ISIL-123", null);
        byte[] blockData = model.getBlock(32);
        reader.bufferedQueue.add(new FeigRFID.BufferedTag("E004010000000002", blockData, 4, null, 1));

        TestWsBrokerClient testClient = new TestWsBrokerClient("test-client");
        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("test-client", "https://localhost:8765", List.of("rfid:gate-01"), null, testClient, true);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "gate-01");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, dispatcher, 50, 1000, false, "gate-01");

        worker.start();

        long deadline = System.currentTimeMillis() + 2000;
        while (testClient.getSentEvents().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        worker.stop();

        assertFalse(testClient.getSentEvents().isEmpty(), "Expected tag read event to be dispatched");
        Event<?> event = testClient.getSentEvents().get(0);
        org.objectspace.rfid.headless.payload.TagReadData data = (org.objectspace.rfid.headless.payload.TagReadData) event.getData();
        assertEquals("E004010000000002", data.uid());
        assertEquals("BOOK-FALLBACK", data.itemId());
        assertNotNull(data.time());
        assertFalse(data.time().isBlank());
        assertDoesNotThrow(() -> java.time.Instant.parse(data.time()));
    }

    @Test
    @DisplayName("Gate worker polls people counters and diagnostics and publishes to rfid.gate.status")
    public void workerPollsAndDispatchesGateStatus() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(true);

        TestWsBrokerClient testClient = new TestWsBrokerClient("test-client");
        HeadlessWsBrokerDispatcher dispatcher = new HeadlessWsBrokerDispatcher("test-client", "https://localhost:8765", List.of("gates"), null, testClient, true);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, dispatcher, 1000L, "gate-north");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, dispatcher, 50, 80, true, "gate-north");

        worker.start();

        long deadline = System.currentTimeMillis() + 2000;
        while (testClient.getSentEvents().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        worker.stop();

        assertFalse(testClient.getSentEvents().isEmpty(), "Expected gate status event to be dispatched");
        Event<?> event = testClient.getSentEvents().get(0);
        assertEquals("rfid.gate.status", event.getType());

        Map<?, ?> data = (Map<?, ?>) event.getData();
        assertEquals("gate-north", data.get("readerId"));
        assertNotNull(data.get("counters"));
        assertNotNull(data.get("diagnostics"));
    }

    @Test
    @DisplayName("RfidWriteService rejects writeTags and writeAfi on readers in buffered mode")
    public void writeOperationsAreRejectedInBufferedMode() throws Exception {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        RfidWriteService writeService = new RfidWriteService(registry, "test-client");

        HeadlessReaderConfig bufferedCfg = new HeadlessReaderConfig("gate-buffered");
        bufferedCfg.setOperatingMode("buffered");

        MockGateReader mockReader = new MockGateReader();
        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, null, 1000L, "gate-buffered");
        HeadlessGateWorker worker = new HeadlessGateWorker(mockReader, callback, null, 100, 1000, false, "gate-buffered");
        HeadlessReaderInstance bufferedInstance = HeadlessReaderInstance.forGateWorker(bufferedCfg, mockReader, callback, worker);

        assertTrue(bufferedInstance.isBufferedMode());

        // Test writeTags rejection
        TagWriteItem item = new TagWriteItem("E004010000000001", "ITEM-1", "ISIL", "CH", 1, 1, 1, "07");
        WriteTagResponseData writeTagResp = writeService.writeTagsOnReader(
                bufferedInstance, List.of(item), "reply-target", "token", "ref-1", null, false).get();

        assertFalse("ok".equals(writeTagResp.status()));
        assertTrue(writeTagResp.error().contains("buffered mode"));
        assertTrue(writeTagResp.error().contains("gate-buffered"));

        // Test writeAfi rejection
        WriteAfiResponseData writeAfiResp = writeService.writeAfiOnReader(
                bufferedInstance, List.of("E004010000000001"), "07", "reply-target", "token", "ref-2", null, false).get();

        assertFalse("ok".equals(writeAfiResp.status()));
        assertTrue(writeAfiResp.error().contains("buffered mode"));
        assertTrue(writeAfiResp.error().contains("gate-buffered"));
    }

    @Test
    @DisplayName("Mixed host and gate reader instances operate concurrently and independently")
    public void mixedHostAndGateInstancesOperateConcurrently() throws Exception {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();

        // Host reader instance
        HeadlessReaderConfig hostCfg = new HeadlessReaderConfig("host-desk");
        hostCfg.setOperatingMode("host");
        MockISO15693Reader hostReader = new MockISO15693Reader();
        HeadlessReaderCallback hostCallback = new HeadlessReaderCallback(null, null, 1000L, "host-desk");
        HeadlessReaderScanner hostScanner = new HeadlessReaderScanner(hostReader, hostCallback, 12, 100, "host-desk");
        HeadlessReaderInstance hostInstance = new HeadlessReaderInstance(hostCfg, hostReader, hostCallback, hostScanner);

        // Gate reader instance
        HeadlessReaderConfig gateCfg = new HeadlessReaderConfig("gate-passage");
        gateCfg.setOperatingMode("buffered");
        MockGateReader gateReader = new MockGateReader();
        HeadlessReaderCallback gateCallback = new HeadlessReaderCallback(null, null, 1000L, "gate-passage");
        HeadlessGateWorker gateWorker = new HeadlessGateWorker(gateReader, gateCallback, null, 50, 1000, false, "gate-passage");
        HeadlessReaderInstance gateInstance = HeadlessReaderInstance.forGateWorker(gateCfg, gateReader, gateCallback, gateWorker);

        registry.setReaderInstances(List.of(hostInstance, gateInstance));

        assertFalse(hostInstance.isBufferedMode());
        assertTrue(gateInstance.isBufferedMode());

        hostInstance.start();
        gateInstance.start();

        Thread.sleep(150);

        assertTrue(gateWorker.isRunning());

        hostInstance.stop();
        gateInstance.stop();

        assertFalse(gateWorker.isRunning());
    }

    @Test
    @DisplayName("Gate worker checks operating mode and verifies Buffered Read Mode upon connection")
    public void workerChecksOperatingModeUponConnection() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(false);
        reader.setBufferedModeState(true);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, null, 1000L, "gate-north");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, null, 50, 1000, false, "gate-north");

        worker.start();
        Thread.sleep(150);

        worker.stop();

        assertTrue(reader.operatingModeChecks.get() > 0, "Gate worker should have verified operating mode");
    }

    @Test
    @DisplayName("Gate worker warns when connected reader is in Host Mode")
    public void workerWarnsWhenInHostMode() throws Exception {
        MockGateReader reader = new MockGateReader();
        reader.setConnected(false);
        reader.setBufferedModeState(false);

        HeadlessReaderCallback callback = new HeadlessReaderCallback(null, null, 1000L, "gate-north");
        HeadlessGateWorker worker = new HeadlessGateWorker(reader, callback, null, 50, 1000, false, "gate-north");

        worker.start();
        Thread.sleep(150);

        worker.stop();

        assertTrue(reader.operatingModeChecks.get() > 0, "Gate worker should have checked operating mode");
    }
}

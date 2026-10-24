package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.library.ISO15693Reader;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessReaderScannerTest {

    @Test
    @DisplayName("Test HeadlessReaderScanner start, scan loop invocation, and graceful stop")
    public void testScannerLifecycleAndExecution() throws Exception {
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicInteger inventoryInvocations = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() throws Exception {
                connected.set(true);
            }

            @Override
            public void init() throws Exception {}

            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
                inventoryInvocations.incrementAndGet();
            }

            @Override
            public boolean isConnected() {
                return connected.get();
            }

            @Override
            public boolean checkConnection() {
                return connected.get();
            }

            @Override
            public String getDeviceInfo() {
                return "Mock FEIG Reader";
            }

            @Override
            public void close() throws Exception {
                connected.set(false);
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 50);

        assertFalse(scanner.isRunning());
        scanner.start();
        assertTrue(scanner.isRunning());

        // Wait for scanner to connect and execute a few cycles
        Thread.sleep(250);

        assertTrue(connected.get(), "Reader should have connected");
        assertTrue(inventoryInvocations.get() >= 2, "Scanner should have invoked inventory multiple times");

        scanner.stop();
        assertFalse(scanner.isRunning());
    }

    @Test
    @DisplayName("Test ensureHostMode does not restore configuration if reader is already in Host Mode")
    public void testEnsureHostModeWhenAlreadyInHostMode() {
        AtomicBoolean restoreCalled = new AtomicBoolean(false);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() throws Exception {}

            @Override
            public void init() throws Exception {}

            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {}

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean checkConnection() {
                return true;
            }

            @Override
            public String getDeviceInfo() {
                return "Mock FEIG Reader";
            }

            @Override
            public boolean isHostMode() {
                return true;
            }

            @Override
            public void restoreHostModeConfig(java.io.File configFile) throws Exception {
                restoreCalled.set(true);
            }

            @Override
            public void close() throws Exception {}
        };

        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 50);
        scanner.ensureHostMode();

        assertFalse(restoreCalled.get(), "restoreHostModeConfig should not be called when already in host mode");
    }

    @Test
    @DisplayName("Test ensureHostMode restores Host Mode configuration without backup when reader is not in Host Mode")
    public void testEnsureHostModeWhenNotInHostMode() {
        AtomicBoolean restoreCalled = new AtomicBoolean(false);
        AtomicBoolean backupCalled = new AtomicBoolean(false);
        AtomicBoolean hostModeState = new AtomicBoolean(false);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() throws Exception {}

            @Override
            public void init() throws Exception {}

            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {}

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean checkConnection() {
                return true;
            }

            @Override
            public String getDeviceInfo() {
                return "Mock FEIG Reader (Scan Mode)";
            }

            @Override
            public boolean isHostMode() {
                return hostModeState.get();
            }

            @Override
            public void backupConfig(java.io.File targetFile) throws Exception {
                backupCalled.set(true);
            }

            @Override
            public void restoreHostModeConfig(java.io.File configFile) throws Exception {
                restoreCalled.set(true);
                assertNotNull(configFile, "Host mode config file must not be null");
                assertTrue(configFile.exists(), "Host mode config file must exist");
                hostModeState.set(true);
            }

            @Override
            public void close() throws Exception {}
        };

        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 50);
        scanner.ensureHostMode();

        assertTrue(restoreCalled.get(), "restoreHostModeConfig should be called when reader is not in host mode");
        assertFalse(backupCalled.get(), "No backup should be created before restoring host mode");
        assertTrue(mockReader.isHostMode(), "Reader state should be updated to host mode");
    }

    @Test
    @DisplayName("Test executeExclusive pauses scanning loop, runs exclusive task, and clears cache")
    public void testExecuteExclusivePausesAndClearsCache() throws Exception {
        AtomicBoolean writeExecuted = new AtomicBoolean(false);
        AtomicInteger inventoryCallsDuringWrite = new AtomicInteger(0);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() throws Exception {}
            @Override
            public void init() throws Exception {}
            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
                if (writeExecuted.get()) {
                    inventoryCallsDuringWrite.incrementAndGet();
                }
            }
            @Override
            public boolean isConnected() { return true; }
            @Override
            public boolean checkConnection() { return true; }
            @Override
            public String getDeviceInfo() { return "Mock FEIG Reader"; }
            @Override
            public void close() throws Exception {}
        };

        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);
        callback.getLastSeenMap().put("E004010011223344", System.currentTimeMillis());
        assertTrue(callback.getLastSeenMap().containsKey("E004010011223344"), "Tag should be cached prior to write");

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 10);
        scanner.start();

        String result = scanner.executeExclusive(r -> {
            writeExecuted.set(true);
            assertTrue(scanner.isPaused(), "Scanner must report paused during executeExclusive");
            Thread.sleep(100);
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertFalse(scanner.isPaused(), "Scanner must resume after executeExclusive");
        assertFalse(callback.getLastSeenMap().containsKey("E004010011223344"), "Debounce cache must be cleared after executeExclusive");

        scanner.stop();
    }

    @Test
    @DisplayName("Test reader disconnect during inventory scan triggers empty callback and clears active tags")
    public void testReaderDisconnectDuringInventoryScanTriggersEmptyCallback() throws Exception {
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicInteger scanCycles = new AtomicInteger(0);

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override
            public void connect() throws Exception {
                connected.set(true);
            }

            @Override
            public void init() throws Exception {}

            @Override
            public void inventory(TagCallback inventoryCallback, int numBlocks) throws Exception {
                int cycle = scanCycles.incrementAndGet();
                if (cycle == 1) {
                    // First cycle: scan a tag
                    inventoryCallback.doIt(1, 1, "NXP", "SLIX", "E004010099887766", new byte[32], 4L, null);
                } else {
                    // Reader gets disconnected
                    connected.set(false);
                    throw new Exception("USB Communication failure: device disconnected");
                }
            }

            @Override
            public boolean isConnected() {
                return connected.get();
            }

            @Override
            public boolean checkConnection() {
                return connected.get();
            }

            @Override
            public String getDeviceInfo() {
                return "Mock FEIG Reader";
            }

            @Override
            public void close() throws Exception {
                closed.set(true);
                connected.set(false);
            }
        };

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 10);
        scanner.start();

        // Wait for scanner to process first cycle and encounter disconnection in second cycle
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            if (closed.get() && !callback.hasActiveTags()) {
                break;
            }
            Thread.sleep(50);
        }

        assertTrue(closed.get(), "Reader should be closed when connection loss is detected");
        assertFalse(callback.hasActiveTags(), "Active tags must be cleared upon reader disconnection");
        assertEquals(1, callback.getClearedEvents(), "Cleared event count must be incremented");

        scanner.stop();
    }

    @Test
    @DisplayName("Test scanner stop triggers empty callback and clears active tags")
    public void testScannerStopClearsActiveTags() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        // Preload an active tag
        callback.doIt(1, 1, "NXP", "SLIX", "E004010099887766", new byte[32], 4);
        assertTrue(callback.hasActiveTags(), "Tag should be active");

        ISO15693Reader mockReader = new ISO15693Reader() {
            @Override public void connect() {}
            @Override public void init() {}
            @Override public void inventory(TagCallback inventoryCallback, int numBlocks) {}
            @Override public boolean isConnected() { return true; }
            @Override public boolean checkConnection() { return true; }
            @Override public String getDeviceInfo() { return "Mock"; }
            @Override public void close() {}
        };

        HeadlessReaderScanner scanner = new HeadlessReaderScanner(mockReader, callback, 12, 100);
        scanner.start();
        scanner.stop();

        assertFalse(callback.hasActiveTags(), "Active tags must be cleared when scanner is stopped");
        assertEquals(1, callback.getClearedEvents(), "Plate cleared event should be triggered upon stop");
    }
}

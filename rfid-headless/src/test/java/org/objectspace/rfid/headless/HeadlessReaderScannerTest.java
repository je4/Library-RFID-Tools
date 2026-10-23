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
}

package org.objectspace.rfid.raspi;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.TagCallback;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.library.ISO15693Reader;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RaspiInventoryScannerTest {

    @Test
    @DisplayName("Test RaspiInventoryScanner start, scan loop invocation, and graceful stop")
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
        RaspiInventoryCallback callback = new RaspiInventoryCallback(processor, 3000L);

        RaspiInventoryScanner scanner = new RaspiInventoryScanner(mockReader, callback, 12, 50);

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
}

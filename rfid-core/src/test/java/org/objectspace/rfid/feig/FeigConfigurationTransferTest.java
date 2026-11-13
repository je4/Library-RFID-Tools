package org.objectspace.rfid.feig;

import de.feig.fedm.TestConfig;
import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeigConfigurationTransferTest {
    @Test
    void failedFullTransferDoesNotSucceedWithPartialPages() {
        TestConfig config = new TestConfig();
        config.transferResult = 130;
        FeigRFID feig = new FeigRFID(new BaseConfiguration());

        Exception error = assertThrows(Exception.class, () -> feig.copyFileToConfig("reader.xml", config));
        assertTrue(error.getMessage().contains("transferXmlFileToReaderCfg"));
        assertEquals(0, config.fallbackWrites);
    }

    @Test
    void successfulTransferWaitsForEeprom() throws Exception {
        TestConfig config = new TestConfig();
        long[] wait = {0};
        FeigRFID feig = new FeigRFID(new BaseConfiguration()) {
            @Override
            public void waitForEepromWrite(long millis) {
                wait[0] = millis;
            }
        };

        feig.copyFileToConfig("reader.xml", config);
        assertEquals(1500, wait[0]);
        assertEquals(0, config.fallbackWrites);
    }

    @Test
    void interruptedWaitDoesNotReportSuccess() {
        FeigRFID feig = new FeigRFID(new BaseConfiguration()) {
            @Override
            public void waitForEepromWrite(long millis) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            assertThrows(InterruptedException.class, () -> feig.copyFileToConfig("reader.xml", new TestConfig()));
        } finally {
            Thread.interrupted();
        }
    }
}
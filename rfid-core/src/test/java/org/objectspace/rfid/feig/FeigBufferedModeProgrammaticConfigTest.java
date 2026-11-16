package org.objectspace.rfid.feig;

import de.feig.fedm.ErrorCode;
import de.feig.fedm.TestConfig;
import de.feig.fedm.readerConfig.ReaderConfig;
import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeigBufferedModeProgrammaticConfigTest {

    @Test
    @DisplayName("Programmatic Buffered Mode configuration sets mode, data selector, filter, and EAS buzzer parameters")
    void configureBufferedModeAppliesAllParameters() throws Exception {
        TestConfig config = new TestConfig();
        long[] wait = {0};

        FeigRFID feig = new FeigRFID(new BaseConfiguration()) {
            @Override
            public void waitForEepromWrite(long millis) {
                wait[0] = millis;
            }
        };

        feig.configureBufferedMode(config, 14, 25);

        assertEquals(1500, wait[0]);

        // Operating Mode
        assertEquals((byte) 0x80, config.parameters.get(ReaderConfig.OperatingMode.Mode));

        // DataSelector
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.UID));
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.Data));
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.Date));
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.Time));
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.AFI));
        assertEquals(true, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSelector.Direction));

        // DataSource
        assertEquals((byte) 0, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSource.FirstDataBlock));
        assertEquals((byte) 14, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.DataSource.NoOfDataBlocks));

        // Filter / Valid Time
        assertEquals(25L, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.Filter.TransponderValidTime));

        // EAS & Alarm Buzzer (Closed AFI)
        assertEquals((byte) 0x01, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.EAS.AlarmMode));
        assertEquals((byte) 0x01, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.Alarm.Buzzer.SettlingMode));
        assertEquals((byte) 20, config.parameters.get(ReaderConfig.OperatingMode.BufferedReadMode.Alarm.Buzzer.SettlingTime));

        // Signaler Buzzer Hardware
        assertEquals((byte) 0x03, config.parameters.get(ReaderConfig.DigitalIO.Signaler.Buzzer.Volume));
        assertEquals((byte) 20, config.parameters.get(ReaderConfig.DigitalIO.Signaler.Buzzer.ActivationTime));
        assertEquals((byte) 0x01, config.parameters.get(ReaderConfig.DigitalIO.Signaler.Buzzer.ActiveFlashFrequency));
    }

    @Test
    @DisplayName("Programmatic Buffered Mode configuration fails if readCompleteConfiguration fails")
    void configureBufferedModeFailsOnReadError() {
        TestConfig config = new TestConfig();
        config.readCompleteResult = 130;

        FeigRFID feig = new FeigRFID(new BaseConfiguration());
        Exception error = assertThrows(Exception.class, () -> feig.configureBufferedMode(config, 12, 20));
        assertTrue(error.getMessage().contains("readCompleteConfiguration"));
    }

    @Test
    @DisplayName("Programmatic Buffered Mode configuration fails if writeCompleteConfiguration fails")
    void configureBufferedModeFailsOnWriteError() {
        TestConfig config = new TestConfig();
        config.writeCompleteResult = 130;

        FeigRFID feig = new FeigRFID(new BaseConfiguration());
        Exception error = assertThrows(Exception.class, () -> feig.configureBufferedMode(config, 12, 20));
        assertTrue(error.getMessage().contains("writeCompleteConfiguration"));
    }
}

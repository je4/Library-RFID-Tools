package org.objectspace.rfid.raspi;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class RaspiConfigHelperTest {

    @Test
    @DisplayName("Test default configuration values when configuration is empty or null")
    public void testDefaultConfigValues() {
        assertEquals(RaspiConfigHelper.DEFAULT_SLEEP_INTERVAL_MS, RaspiConfigHelper.getSleepInterval(null));
        assertEquals(RaspiConfigHelper.DEFAULT_NUM_BLOCKS, RaspiConfigHelper.getNumBlocks(null));
        assertEquals(RaspiConfigHelper.DEFAULT_DEBOUNCE_MILLIS, RaspiConfigHelper.getDebounceMillis(null));

        BaseConfiguration emptyCfg = new BaseConfiguration();
        assertEquals(300, RaspiConfigHelper.getSleepInterval(emptyCfg));
        assertEquals(12, RaspiConfigHelper.getNumBlocks(emptyCfg));
        assertEquals(3000L, RaspiConfigHelper.getDebounceMillis(emptyCfg));
    }

    @Test
    @DisplayName("Test configuration parsing without <inventory> UI section")
    public void testConfigParsingWithoutInventorySection() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <device>
                        <select>feig</select>
                        <feig>
                            <type>usb</type>
                        </feig>
                    </device>
                    <database>
                        <active>false</active>
                    </database>
                    <webservice>
                        <active>true</active>
                        <target_url>https://api.library.org/scan</target_url>
                        <http_method>POST</http_method>
                    </webservice>
                    <numblocks>16</numblocks>
                    <sleep>500</sleep>
                    <debounce>4000</debounce>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-headless-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = RaspiConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertEquals("feig", config.getString("device.select"));
        assertEquals("usb", config.getString("device.feig.type"));
        assertFalse(config.getBoolean("database.active"));
        assertTrue(config.getBoolean("webservice.active"));
        assertEquals(16, RaspiConfigHelper.getNumBlocks(config));
        assertEquals(500, RaspiConfigHelper.getSleepInterval(config));
        assertEquals(4000L, RaspiConfigHelper.getDebounceMillis(config));
    }

    @Test
    @DisplayName("Test configuration parsing with standard inventory.xml including <inventory> section")
    public void testConfigParsingWithStandardInventoryXml() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <device>
                        <select>feig</select>
                        <feig>
                            <type>usb</type>
                        </feig>
                    </device>
                    <numblocks>12</numblocks>
                    <inventory>
                        <ui>modern</ui>
                        <theme>system</theme>
                        <sleep>250</sleep>
                    </inventory>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-standard-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = RaspiConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertEquals(250, RaspiConfigHelper.getSleepInterval(config));
        assertEquals(12, RaspiConfigHelper.getNumBlocks(config));
        assertEquals(3000L, RaspiConfigHelper.getDebounceMillis(config));
    }
}

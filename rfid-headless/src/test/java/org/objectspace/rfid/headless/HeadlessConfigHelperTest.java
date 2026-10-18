package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessConfigHelperTest {

    @Test
    @DisplayName("Test default configuration values when configuration is empty or null")
    public void testDefaultConfigValues() {
        assertEquals(HeadlessConfigHelper.DEFAULT_SLEEP_INTERVAL_MS, HeadlessConfigHelper.getSleepInterval(null));
        assertEquals(HeadlessConfigHelper.DEFAULT_NUM_BLOCKS, HeadlessConfigHelper.getNumBlocks(null));
        assertEquals(HeadlessConfigHelper.DEFAULT_DEBOUNCE_MILLIS, HeadlessConfigHelper.getDebounceMillis(null));

        BaseConfiguration emptyCfg = new BaseConfiguration();
        assertEquals(300, HeadlessConfigHelper.getSleepInterval(emptyCfg));
        assertEquals(12, HeadlessConfigHelper.getNumBlocks(emptyCfg));
        assertEquals(3000L, HeadlessConfigHelper.getDebounceMillis(emptyCfg));
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

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertEquals("feig", config.getString("device.select"));
        assertEquals("usb", config.getString("device.feig.type"));
        assertFalse(config.getBoolean("database.active"));
        assertTrue(config.getBoolean("webservice.active"));
        assertEquals(16, HeadlessConfigHelper.getNumBlocks(config));
        assertEquals(500, HeadlessConfigHelper.getSleepInterval(config));
        assertEquals(4000L, HeadlessConfigHelper.getDebounceMillis(config));
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

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertEquals(250, HeadlessConfigHelper.getSleepInterval(config));
        assertEquals(12, HeadlessConfigHelper.getNumBlocks(config));
        assertEquals(3000L, HeadlessConfigHelper.getDebounceMillis(config));
    }
    @Test
    @DisplayName("Test WSBroker nested configuration parsing and active flag")
    public void testWsBrokerNestedConfigParsing() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <active>true</active>
                        <name>reader-unit-1</name>
                        <url>https://broker.local:8765</url>
                        <channels>
                            <channel>all</channel>
                            <channel>special</channel>
                        </channels>
                    </wsbroker>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-wsbroker-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertTrue(HeadlessConfigHelper.isWsBrokerActive(config));
        assertEquals("reader-unit-1", HeadlessConfigHelper.getClientName(config));
        assertEquals("https://broker.local:8765", HeadlessConfigHelper.getWsBrokerEndpoint(config));
        assertEquals(java.util.List.of("all", "special"), HeadlessConfigHelper.getChannels(config));
    }

    @Test
    @DisplayName("Test WSBroker disabled when active is false")
    public void testWsBrokerDisabledWhenActiveFalse() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <active>false</active>
                        <name>reader-unit-2</name>
                        <url>https://broker.local:8765</url>
                    </wsbroker>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-wsbroker-disabled-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);
        assertFalse(HeadlessConfigHelper.isWsBrokerActive(config));
        assertEquals("reader-unit-2", HeadlessConfigHelper.getClientName(config));
        assertEquals("https://broker.local:8765", HeadlessConfigHelper.getWsBrokerEndpoint(config));
    }
}

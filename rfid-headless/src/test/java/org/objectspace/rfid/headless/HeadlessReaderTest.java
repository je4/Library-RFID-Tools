package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessReaderTest {

    @Test
    @DisplayName("Test HeadlessReader initialization with configuration and override options")
    public void testHeadlessReaderInitialization() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <device>
                        <select>feig</select>
                        <feig>
                            <type>usb</type>
                        </feig>
                    </device>
                    <numblocks>10</numblocks>
                    <sleep>400</sleep>
                    <debounce>2500</debounce>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-test-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());

        // Test with defaults from XML
        HeadlessReader app = new HeadlessReader(config, null, null, null);
        assertNotNull(app.getReader());
        assertNotNull(app.getCallback());
        assertNotNull(app.getScanner());
        assertNotNull(app.getTagScanProcessor());
        assertEquals(2500L, app.getCallback().getDebounceMillis());

        // Test with CLI overrides
        HeadlessReader appWithOverrides = new HeadlessReader(config, 5000L, 100, 16);
        assertEquals(5000L, appWithOverrides.getCallback().getDebounceMillis());
    }

    @Test
    @DisplayName("Test HeadlessReader initialization with multi-reader configuration and lifecycle management")
    public void testMultiReaderInitializationAndLifecycle() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <readers>
                        <reader>
                            <reader_id>gate-01</reader_id>
                            <readonly>false</readonly>
                            <feig>
                                <id>52E101</id>
                                <type>usb</type>
                            </feig>
                            <debounce>2000</debounce>
                            <sleep>200</sleep>
                            <numblocks>12</numblocks>
                        </reader>
                        <reader>
                            <reader_id>return-02</reader_id>
                            <readonly>true</readonly>
                            <feig>
                                <id>52E102</id>
                                <type>usb</type>
                            </feig>
                            <debounce>1500</debounce>
                            <sleep>150</sleep>
                            <numblocks>16</numblocks>
                        </reader>
                    </readers>
                    <numblocks>10</numblocks>
                    <sleep>300</sleep>
                    <debounce>3000</debounce>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-multi-test-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());

        HeadlessReader app = new HeadlessReader(config, null, null, null);
        assertEquals(2, app.getReaderInstances().size());

        HeadlessReaderInstance inst1 = app.getReaderInstance("gate-01");
        assertNotNull(inst1);
        assertEquals("gate-01", inst1.getReaderId());
        assertFalse(inst1.isReadOnly());
        assertEquals(2000L, inst1.getCallback().getDebounceMillis());
        assertEquals("52E101", inst1.getConfig().getDeviceId());

        HeadlessReaderInstance inst2 = app.getReaderInstance("return-02");
        assertNotNull(inst2);
        assertEquals("return-02", inst2.getReaderId());
        assertTrue(inst2.isReadOnly());
        assertEquals(1500L, inst2.getCallback().getDebounceMillis());
        assertEquals("52E102", inst2.getConfig().getDeviceId());

        // Verify dispatcher has registered both instances
        if (app.getWsBrokerDispatcher() != null) {
            assertEquals(2, app.getWsBrokerDispatcher().getReaderInstances().size());
            assertNotNull(app.getWsBrokerDispatcher().getReaderInstance("gate-01"));
            assertNotNull(app.getWsBrokerDispatcher().getReaderInstance("return-02"));
        }
    }

    @Test
    @DisplayName("Test setLogLevel modifies Logback root logger level")
    public void testSetLogLevel() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level initial = root.getLevel();

        try {
            HeadlessReader.setLogLevel(ch.qos.logback.classic.Level.DEBUG);
            assertEquals(ch.qos.logback.classic.Level.DEBUG, root.getLevel());

            HeadlessReader.setLogLevel(ch.qos.logback.classic.Level.INFO);
            assertEquals(ch.qos.logback.classic.Level.INFO, root.getLevel());
        } finally {
            root.setLevel(initial);
        }
    }
}

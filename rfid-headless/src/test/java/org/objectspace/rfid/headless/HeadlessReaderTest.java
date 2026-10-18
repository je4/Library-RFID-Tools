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

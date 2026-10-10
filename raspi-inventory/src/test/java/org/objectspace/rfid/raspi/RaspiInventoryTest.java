package org.objectspace.rfid.raspi;

import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class RaspiInventoryTest {

    @Test
    @DisplayName("Test RaspiInventory initialization with configuration and override options")
    public void testRaspiInventoryInitialization() throws Exception {
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

        XMLConfiguration config = RaspiConfigHelper.loadConfiguration(tempXml.getAbsolutePath());

        // Test with defaults from XML
        RaspiInventory app = new RaspiInventory(config, null, null, null);
        assertNotNull(app.getReader());
        assertNotNull(app.getCallback());
        assertNotNull(app.getScanner());
        assertNotNull(app.getTagScanProcessor());
        assertEquals(2500L, app.getCallback().getDebounceMillis());

        // Test with CLI overrides
        RaspiInventory appWithOverrides = new RaspiInventory(config, 5000L, 100, 16);
        assertEquals(5000L, appWithOverrides.getCallback().getDebounceMillis());
    }
}

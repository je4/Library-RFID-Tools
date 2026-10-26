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

    @Test
    @DisplayName("Test debounce interval parsing with alias keys and precedence")
    public void testDebounceKeyAliasesAndPrecedence() throws Exception {
        // 1. Direct <debounce>
        BaseConfiguration cfg1 = new BaseConfiguration();
        cfg1.setProperty("debounce", 5000L);
        assertEquals(5000L, HeadlessConfigHelper.getDebounceMillis(cfg1));

        // 2. <inventory.debounce>
        BaseConfiguration cfg2 = new BaseConfiguration();
        cfg2.setProperty("inventory.debounce", 2500L);
        assertEquals(2500L, HeadlessConfigHelper.getDebounceMillis(cfg2));

        // 3. <resend_interval>
        BaseConfiguration cfg3 = new BaseConfiguration();
        cfg3.setProperty("resend_interval", 4000L);
        assertEquals(4000L, HeadlessConfigHelper.getDebounceMillis(cfg3));

        // 4. <resend_time>
        BaseConfiguration cfg4 = new BaseConfiguration();
        cfg4.setProperty("resend_time", 3500L);
        assertEquals(3500L, HeadlessConfigHelper.getDebounceMillis(cfg4));

        // 5. <tag_resend_time>
        BaseConfiguration cfg5 = new BaseConfiguration();
        cfg5.setProperty("tag_resend_time", 4500L);
        assertEquals(4500L, HeadlessConfigHelper.getDebounceMillis(cfg5));

        // 6. <cooldown>
        BaseConfiguration cfg6 = new BaseConfiguration();
        cfg6.setProperty("cooldown", 2000L);
        assertEquals(2000L, HeadlessConfigHelper.getDebounceMillis(cfg6));

        // 7. Precedence test: debounce overrides resend_interval
        BaseConfiguration cfgPrecedence = new BaseConfiguration();
        cfgPrecedence.setProperty("debounce", 1500L);
        cfgPrecedence.setProperty("resend_interval", 6000L);
        assertEquals(1500L, HeadlessConfigHelper.getDebounceMillis(cfgPrecedence));

        // 8. Negative/invalid value falls back to default
        BaseConfiguration cfgInvalid = new BaseConfiguration();
        cfgInvalid.setProperty("debounce", -100L);
        assertEquals(HeadlessConfigHelper.DEFAULT_DEBOUNCE_MILLIS, HeadlessConfigHelper.getDebounceMillis(cfgInvalid));
    }

    @Test
    @DisplayName("Test getReaderConfigs with multi-reader XML configuration")
    public void testGetReaderConfigsMultiReaderXml() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <readers>
                        <reader>
                            <reader_id>gate-01</reader_id>
                            <readonly>false</readonly>
                            <feig>
                                <id>52E688</id>
                                <type>usb</type>
                            </feig>
                            <debounce>2500</debounce>
                            <sleep>200</sleep>
                            <numblocks>16</numblocks>
                        </reader>
                        <reader>
                            <reader_id>return-station</reader_id>
                            <readonly>true</readonly>
                            <feig>
                                <id>52E689</id>
                                <type>usb</type>
                            </feig>
                        </reader>
                    </readers>
                    <numblocks>12</numblocks>
                    <sleep>300</sleep>
                    <debounce>3000</debounce>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-multi-reader-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);

        java.util.List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
        assertEquals(2, readerConfigs.size());

        HeadlessReaderConfig r1 = readerConfigs.get(0);
        assertEquals("gate-01", r1.getReaderId());
        assertFalse(r1.isReadOnly());
        assertEquals("52E688", r1.getDeviceId());
        assertEquals("usb", r1.getType());
        assertEquals(2500L, r1.getDebounceMillis());
        assertEquals(200, r1.getSleepInterval());
        assertEquals(16, r1.getNumBlocks());

        // Test effective configuration of r1
        org.apache.commons.configuration2.AbstractConfiguration c1 = r1.toConfiguration();
        assertEquals("feig", c1.getString("device.select"));
        assertEquals("52E688", c1.getString("device.feig.id"));
        assertEquals("usb", c1.getString("device.feig.type"));
        assertEquals(16, c1.getInt("numblocks"));
        assertEquals(200, c1.getInt("sleep"));
        assertEquals(2500L, c1.getLong("debounce"));

        HeadlessReaderConfig r2 = readerConfigs.get(1);
        assertEquals("return-station", r2.getReaderId());
        assertTrue(r2.isReadOnly());
        assertEquals("52E689", r2.getDeviceId());
        assertEquals("usb", r2.getType());
        // Defaults inherited from root config
        assertEquals(3000L, r2.getDebounceMillis());
        assertEquals(300, r2.getSleepInterval());
        assertEquals(12, r2.getNumBlocks());
    }

    @Test
    @DisplayName("Test getReaderConfigs fallback for single-device legacy configuration")
    public void testGetReaderConfigsLegacyFallback() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <device>
                        <select>feig</select>
                        <feig>
                            <id>52E100</id>
                            <type>usb</type>
                        </feig>
                    </device>
                    <wsbroker>
                        <name>rfid:station-01</name>
                    </wsbroker>
                    <numblocks>14</numblocks>
                    <sleep>400</sleep>
                    <debounce>3500</debounce>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-legacy-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);

        java.util.List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
        assertEquals(1, readerConfigs.size());

        HeadlessReaderConfig r = readerConfigs.get(0);
        assertEquals("rfid:station-01", r.getReaderId());
        assertFalse(r.isReadOnly());
        assertEquals("52E100", r.getDeviceId());
        assertEquals("usb", r.getType());
        assertEquals("feig", r.getReaderType());
        assertEquals(3500L, r.getDebounceMillis());
        assertEquals(400, r.getSleepInterval());
        assertEquals(14, r.getNumBlocks());

        org.apache.commons.configuration2.AbstractConfiguration c = r.toConfiguration();
        assertEquals("feig", c.getString("device.select"));
        assertEquals("52E100", c.getString("device.feig.id"));
        assertEquals("usb", c.getString("device.feig.type"));
    }

    @Test
    @DisplayName("Test getReaderConfigs with null or empty configuration")
    public void testGetReaderConfigsNullOrEmpty() {
        java.util.List<HeadlessReaderConfig> nullConfigs = HeadlessConfigHelper.getReaderConfigs(null);
        assertEquals(1, nullConfigs.size());
        assertEquals(HeadlessConfigHelper.DEFAULT_CLIENT_NAME, nullConfigs.get(0).getReaderId());
        assertFalse(nullConfigs.get(0).isReadOnly());

        BaseConfiguration emptyCfg = new BaseConfiguration();
        java.util.List<HeadlessReaderConfig> emptyConfigs = HeadlessConfigHelper.getReaderConfigs(emptyCfg);
        assertEquals(1, emptyConfigs.size());
        assertEquals(HeadlessConfigHelper.DEFAULT_CLIENT_NAME, emptyConfigs.get(0).getReaderId());
    }

    @Test
    @DisplayName("Test tagfeatures hierarchical structure preservation in toConfiguration()")
    public void testTagFeaturesHierarchicalPreservationInToConfiguration() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <readers>
                        <reader>
                            <reader_id>gate-01</reader_id>
                            <feig>
                                <id>52E688</id>
                            </feig>
                        </reader>
                    </readers>
                    <tagfeatures>
                        <tag>
                            <name><![CDATA[ISO 15693 : NXP I-Code SLIX]]></name>
                            <blocksize>4</blocksize>
                            <usermemory>896</usermemory>
                            <blocks>32</blocks>
                        </tag>
                        <tag>
                            <name><![CDATA[ISO15693]]></name>
                            <blocksize>4</blocksize>
                            <usermemory>896</usermemory>
                            <blocks>32</blocks>
                        </tag>
                    </tagfeatures>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-tagfeatures-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);

        java.util.List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
        assertEquals(1, readerConfigs.size());

        HeadlessReaderConfig r1 = readerConfigs.get(0);
        org.apache.commons.configuration2.AbstractConfiguration effectiveConfig = r1.toConfiguration();

        // Check that hierarchical tagfeatures keys are accessible with indices as required by ISO15693Feig
        java.util.List<Object> tagNames = effectiveConfig.getList("tagfeatures.tag.name");
        assertEquals(2, tagNames.size());
        assertEquals("ISO 15693 : NXP I-Code SLIX", effectiveConfig.getString("tagfeatures.tag(0).name"));
        assertEquals(896, effectiveConfig.getInt("tagfeatures.tag(0).usermemory"));
        assertEquals(4, effectiveConfig.getInt("tagfeatures.tag(0).blocksize"));

        assertEquals("ISO15693", effectiveConfig.getString("tagfeatures.tag(1).name"));
        assertEquals(896, effectiveConfig.getInt("tagfeatures.tag(1).usermemory"));
        assertEquals(4, effectiveConfig.getInt("tagfeatures.tag(1).blocksize"));
    }

    @Test
    @DisplayName("Test reader-specific channels parsing and fallback to global channels")
    public void testReaderSpecificChannelsParsing() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <wsbroker>
                        <channels>
                            <channel>global-ch1</channel>
                            <channel>global-ch2</channel>
                        </channels>
                    </wsbroker>
                    <readers>
                        <reader>
                            <reader_id>reader-custom</reader_id>
                            <channels>
                                <channel>custom-ch1</channel>
                                <channel>custom-ch2</channel>
                            </channels>
                        </reader>
                        <reader>
                            <reader_id>reader-fallback</reader_id>
                        </reader>
                    </readers>
                </configuration>
                """;

        File tempXml = File.createTempFile("inventory-channels-", ".xml");
        tempXml.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempXml)) {
            writer.write(xmlContent);
        }

        XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(tempXml.getAbsolutePath());
        assertNotNull(config);

        java.util.List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
        assertEquals(2, readerConfigs.size());

        HeadlessReaderConfig customReader = readerConfigs.get(0);
        assertEquals(2, customReader.getChannels().size());
        assertTrue(customReader.getChannels().contains("custom-ch1"));
        assertTrue(customReader.getChannels().contains("custom-ch2"));

        HeadlessReaderConfig fallbackReader = readerConfigs.get(1);
        assertEquals(2, fallbackReader.getChannels().size());
        assertTrue(fallbackReader.getChannels().contains("global-ch1"));
        assertTrue(fallbackReader.getChannels().contains("global-ch2"));
    }

    @Test
    @DisplayName("Test loading actual inventory.xml and rfid-headless/inventory.xml files")
    public void testActualInventoryXmlFiles() throws Exception {
        String[] configPaths = new String[]{"../inventory.xml", "inventory.xml"};
        for (String path : configPaths) {
            File f = new File(path);
            if (!f.exists()) {
                // If run from root or module, check alternative
                f = new File("rfid-headless/" + path);
            }
            if (f.exists()) {
                XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(f.getAbsolutePath());
                assertNotNull(config, "Configuration must load from " + f.getAbsolutePath());

                java.util.List<HeadlessReaderConfig> readerConfigs = HeadlessConfigHelper.getReaderConfigs(config);
                assertNotNull(readerConfigs);
                assertEquals(1, readerConfigs.size());

                HeadlessReaderConfig primaryReader = readerConfigs.get(0);
                assertEquals("reader-01", primaryReader.getReaderId());
                assertFalse(primaryReader.isReadOnly());
                assertEquals("usb", primaryReader.getType());
                assertEquals(12, primaryReader.getNumBlocks());
                assertEquals(300, primaryReader.getSleepInterval());
                assertEquals(20000, primaryReader.getDebounceMillis());
                assertTrue(primaryReader.getChannels().contains("rfid"));
                assertTrue(primaryReader.getChannels().contains("rfid:reader-01"));

                // Verify hierarchical preservation in effective config
                org.apache.commons.configuration2.AbstractConfiguration effective = primaryReader.toConfiguration();
                assertNotNull(effective);
                assertEquals("ISO 15693 : NXP I-Code SLIX", effective.getString("tagfeatures.tag(0).name"));
                assertEquals(896, effective.getInt("tagfeatures.tag(0).usermemory"));
                assertEquals(4, effective.getInt("tagfeatures.tag(0).blocksize"));
                assertEquals(32, effective.getInt("tagfeatures.tag(0).blocks"));
            }
        }
    }

    @Test
    @DisplayName("Test streamlined inventory.xml and inventory.xml.template without database, webservice, or inventory sections")
    public void testStreamlinedInventoryXmlAndTemplate() throws Exception {
        String[] filesToCheck = new String[]{
            "rfid-headless/inventory.xml",
            "rfid-headless/inventory.xml.template"
        };
        for (String relativePath : filesToCheck) {
            File f = new File(relativePath);
            if (!f.exists()) {
                // If executed from module directory (rfid-headless/)
                f = new File(relativePath.replace("rfid-headless/", ""));
            }
            if (f.exists()) {
                XMLConfiguration config = HeadlessConfigHelper.loadConfiguration(f.getAbsolutePath());
                assertNotNull(config, "Config should load from " + f.getAbsolutePath());

                // Verify database, webservice, and inventory sections are absent
                assertFalse(config.containsKey("database.active"), "database.active should not be present in " + f.getName());
                assertFalse(config.containsKey("webservice.active"), "webservice.active should not be present in " + f.getName());
                assertFalse(config.containsKey("inventory.ui"), "inventory.ui should not be present in " + f.getName());
                assertFalse(config.containsKey("inventory.sleep"), "inventory.sleep should not be present in " + f.getName());

                // Verify TagScanProcessor initializes with inactive database and webservice
                try (org.objectspace.rfid.core.TagScanProcessor processor = new org.objectspace.rfid.core.TagScanProcessor(config)) {
                    assertFalse(processor.isDatabaseConfigured());
                    assertFalse(processor.isDatabaseConnected());
                    assertFalse(processor.isWebserviceActive());
                }

                // Verify default sleep interval fallback
                assertEquals(HeadlessConfigHelper.DEFAULT_SLEEP_INTERVAL_MS, HeadlessConfigHelper.getSleepInterval(config));
            }
        }
    }
}

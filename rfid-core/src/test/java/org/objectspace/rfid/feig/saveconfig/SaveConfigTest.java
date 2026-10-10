/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * This file is part of RFID Library Tools.
 *******************************************************************************/
package org.objectspace.rfid.feig.saveconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.feig.FeigRFID;
import org.objectspace.rfid.feig.ISO15693Feig;

public class SaveConfigTest {

    @Test
    public void testSaveConfigInstantiation() {
        SaveConfig sc = new SaveConfig();
        assertNotNull(sc);
    }

    @Test
    public void testHostModeConfigFileStructure() throws Exception {
        File configFile = new File("reader_host_mode_config.xml");
        if (!configFile.exists()) {
            configFile = new File("rfid-inventory/reader_host_mode_config.xml");
        }
        assertTrue(configFile.exists(), "Host mode config file must exist");
        assertTrue(configFile.isFile(), "Host mode config must be a regular file");
        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("ID ISC.MR102"), "Config should be for ID ISC.MR102");
        assertTrue(content.contains("Reader EEPROM-Parameter"), "Config should contain EEPROM parameters");
        assertTrue(content.contains("<CFG5 b0=\"31\""), "Config should contain CFG5 with Host-Mode RF ON");
        assertTrue(content.contains("<CFG0 "), "Config should contain CFG0 tag");
        assertTrue(content.contains("<CFG33 "), "Config should contain CFG33 tag");
        assertTrue(content.contains("<CFG40 "), "Config should contain CFG40 tag");
    }

    @Test
    public void testFormatError() {
        assertEquals("OK", FeigRFID.formatError(0));
        String status130 = FeigRFID.formatError(130);
        assertTrue(status130.contains("130") || status130.toLowerCase().contains("cmd"), "Status 130 should format properly");
        String errMinus130 = FeigRFID.formatError(-130);
        assertNotNull(errMinus130);
    }

    @Test
    public void testFeigRFIDMR102DetectionAndErrorTracking() {
        BaseConfiguration config = new BaseConfiguration();
        FeigRFID feig = new FeigRFID(config);

        assertFalse(feig.isMR102());
        assertFalse(feig.hasError());
        assertEquals(null, feig.getLastErrorMessage());

        feig.setMR102(true);
        assertTrue(feig.isMR102());
        assertTrue(feig.isMR102Detected());

        feig.setCommunicationError(true, "Communication timeout");
        assertTrue(feig.hasError());
        assertTrue(feig.hasCommunicationError());
        assertEquals("Communication timeout", feig.getLastErrorMessage());

        feig.setCommunicationError(false, null);
        assertFalse(feig.hasError());
        assertEquals(null, feig.getLastErrorMessage());
    }

    @Test
    public void testISO15693FeigDelegation() {
        BaseConfiguration config = new BaseConfiguration();
        ISO15693Feig reader = new ISO15693Feig(config);

        assertFalse(reader.isMR102());
        assertFalse(reader.hasError());

        reader.getFeigRFID().setMR102(true);
        assertTrue(reader.isMR102());

        reader.getFeigRFID().setCommunicationError(true, "Connect failed");
        assertTrue(reader.hasError());
        assertEquals("Connect failed", reader.getLastErrorDetails());
    }

    @Test
    public void testWaitForEepromWrite() {
        BaseConfiguration config = new BaseConfiguration();
        FeigRFID feig = new FeigRFID(config);

        long start = System.currentTimeMillis();
        feig.waitForEepromWrite(50);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 40, "Should wait for at least ~40ms");
        assertTrue(feig.getStartupLogs().stream().anyMatch(log -> log.contains("EEPROM-Schreibvorgang")), "Should log EEPROM wait");
    }

    @Test
    public void testSystemResetThrowsWhenUninitialized() {
        BaseConfiguration config = new BaseConfiguration();
        FeigRFID feig = new FeigRFID(config);

        try {
            feig.systemReset();
            org.junit.jupiter.api.Assertions.fail("Should throw exception when reader not initialized");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not initialized"));
        }
    }

    @Test
    public void testResolveHostModeConfigFile() {
        BaseConfiguration config = new BaseConfiguration();
        File resolved = FeigRFID.resolveHostModeConfigFile(config);
        assertNotNull(resolved);
        assertTrue(resolved.exists(), "Resolved host mode config file must exist");

        ISO15693Feig reader = new ISO15693Feig(config);
        File readerResolved = reader.resolveHostModeConfigFile();
        assertNotNull(readerResolved);
        assertTrue(readerResolved.exists(), "Reader resolved host mode config file must exist");
    }
}

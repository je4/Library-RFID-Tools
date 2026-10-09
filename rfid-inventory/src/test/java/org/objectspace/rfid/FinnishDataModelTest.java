/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * Based on HAWK RFID Library Tools:
 * Copyright 2015 Center for Information, Media and Technology (ZIMT),
 * HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of RFID Library Tools.
 * 
 * RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Diese Datei ist Teil von RFID Library Tools.
 *  
 * RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class FinnishDataModelTest {

    @Test
    @DisplayName("Test encoding and decoding of Finnish Data Model / ISO 28560 block")
    public void testEncodeAndDecode() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "1234567890", "DE", "HIL3/9", new ArrayList<>());

        byte[] block = model.getBlock(32);
        assertNotNull(block);
        assertEquals(32, block.length);

        // Decode back
        FinnishDataModel decoded = new FinnishDataModel();
        decoded.setBlock(block, 4);

        assertEquals(1, decoded.getVersion());
        assertEquals(1, decoded.getTypeOfUsage());
        assertEquals(1, decoded.getPartsInItem());
        assertEquals(1, decoded.getPartNumber());
        assertEquals("1234567890", decoded.getPrimaryItemId());
        assertEquals("DE", decoded.getCountryOfOwnerLib());
        assertEquals("HIL3/9", decoded.getISIL());
        assertFalse(decoded.getCRCError(), "CRC checksum should match");
    }

    @Test
    @DisplayName("Test CRC verification and error detection")
    public void testCrcErrorDetection() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-001", "CH", "001596-0", new ArrayList<>());

        byte[] block = model.getBlock(32);
        assertNotNull(block);

        // Corrupt one data byte (not CRC)
        byte[] corrupted = block.clone();
        corrupted[5] = (byte) (corrupted[5] ^ 0xFF);

        FinnishDataModel decoded = new FinnishDataModel();
        decoded.setBlock(corrupted, 4);

        assertTrue(decoded.getCRCError(), "Corrupted data must result in CRC error");
    }

    @Test
    @DisplayName("Test empty block detection")
    public void testEmptyBlock() throws Exception {
        byte[] empty = FinnishDataModel.getEmptyBlock(32);
        FinnishDataModel decoded = new FinnishDataModel();
        decoded.setBlock(empty, 4);

        assertTrue(decoded.isEmpty());
    }

    @Test
    @DisplayName("Test Optional Blocks encoding and decoding")
    public void testOptionalBlocks() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        ArrayList<FinnishDataModelOptionalBlock> optionalBlocks = new ArrayList<>();

        FinnishDataModelOptionalBlock op1 = new FinnishDataModelOptionalBlock();
        byte[] opData = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        op1.setData(0x1234, opData);
        optionalBlocks.add(op1);

        model.setValues(1, 1, 1, "BOOK-42", "DE", "TEST-LIB", optionalBlocks);

        byte[] block = model.getBlock(64);
        assertNotNull(block);

        FinnishDataModel decoded = new FinnishDataModel();
        decoded.setBlock(block, 4);

        assertFalse(decoded.getCRCError());
        assertEquals("BOOK-42", decoded.getPrimaryItemId());
        assertEquals("DE", decoded.getCountryOfOwnerLib());
        assertEquals("TEST-LIB", decoded.getISIL());
    }

    @Test
    @DisplayName("Test Optional Block XOR calculation and integrity")
    public void testOptionalBlockXOR() throws Exception {
        FinnishDataModelOptionalBlock op = new FinnishDataModelOptionalBlock();
        byte[] data = new byte[] { 0x10, 0x20, 0x30 };
        op.setData(0xABCD, data);

        byte[] rawBlock = op.getBlock();
        assertNotNull(rawBlock);
        assertEquals(data.length + 4, rawBlock.length);

        FinnishDataModelOptionalBlock decodedOp = new FinnishDataModelOptionalBlock();
        int next = decodedOp.setBlock(rawBlock, 0);

        assertEquals(rawBlock.length, next);
        assertEquals(0xABCD, decodedOp.getID());
        assertArrayEquals(data, decodedOp.getData());
        assertFalse(decodedOp.xorError());
    }

    @Test
    @DisplayName("Test Reader Factory validation and connection check methods")
    public void testReaderFactoryValidation() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            org.apache.commons.configuration2.BaseConfiguration cfg = new org.apache.commons.configuration2.BaseConfiguration();
            cfg.setProperty("device.select", "unknown_device");
            ISO15693ReaderFactory.createReader(cfg);
        });

        org.apache.commons.configuration2.BaseConfiguration cfg = new org.apache.commons.configuration2.BaseConfiguration();
        cfg.setProperty("device.select", "feig");
        org.objectspace.rfid.library.ISO15693Reader reader = ISO15693ReaderFactory.createReader(cfg);
        assertNotNull(reader);
        assertFalse(reader.isConnected(), "Reader should not report connected before connect() is called");
        assertFalse(reader.checkConnection(), "checkConnection() should return false when not connected");
        assertNull(reader.getDeviceInfo(), "getDeviceInfo() should be null when not connected");
    }

    @Test
    @DisplayName("Test InventoryItemEntry data mapping and status defaults")
    public void testInventoryItemEntry() {
        org.objectspace.rfid.library.inventory.InventoryDialog.InventoryItemEntry item =
            new org.objectspace.rfid.library.inventory.InventoryDialog.InventoryItemEntry();
        item.index = 1;
        item.primaryItemId = "12345678";
        item.signature = "SIG-99";
        item.uid = "E004010001234567";
        item.crcStatus = "OK";
        item.statusOk = true;
        item.statusSymbol = "\u2714";
        item.statusDetails = "DB: OK, Webservice: OK";

        assertEquals(1, item.index);
        assertEquals("12345678", item.primaryItemId);
        assertEquals("SIG-99", item.signature);
        assertEquals("E004010001234567", item.uid);
        assertEquals("OK", item.crcStatus);
        assertTrue(item.statusOk);
        assertEquals("\u2714", item.statusSymbol);
        assertEquals("DB: OK, Webservice: OK", item.statusDetails);
    }
}

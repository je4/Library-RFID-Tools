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
    @DisplayName("Test InventoryItemEntry data mapping")
    public void testInventoryItemEntry() {
        org.objectspace.rfid.library.inventory.InventoryDialog.InventoryItemEntry item =
            new org.objectspace.rfid.library.inventory.InventoryDialog.InventoryItemEntry();
        item.index = 1;
        item.primaryItemId = "12345678";
        item.signature = "SIG-99";
        item.uid = "E004010001234567";
        item.crcStatus = "OK";

        assertEquals(1, item.index);
        assertEquals("12345678", item.primaryItemId);
        assertEquals("SIG-99", item.signature);
        assertEquals("E004010001234567", item.uid);
        assertEquals("OK", item.crcStatus);
    }
}

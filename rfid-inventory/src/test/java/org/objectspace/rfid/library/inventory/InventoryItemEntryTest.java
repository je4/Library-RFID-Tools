package org.objectspace.rfid.library.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class InventoryItemEntryTest {

    @Test
    @DisplayName("Test InventoryItemEntry data mapping and status defaults")
    public void testInventoryItemEntry() {
        InventoryDialog.InventoryItemEntry item =
            new InventoryDialog.InventoryItemEntry();
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

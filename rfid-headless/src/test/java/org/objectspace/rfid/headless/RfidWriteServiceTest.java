package org.objectspace.rfid.headless;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.headless.payload.TagWriteItem;
import org.objectspace.rfid.headless.payload.WriteAfiResponseData;
import org.objectspace.rfid.headless.payload.WriteTagResponseData;
import org.objectspace.rfid.library.ISO15693Reader;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class RfidWriteServiceTest {

    @Test
    @DisplayName("Test writeTags rejects when reader is read-only")
    void testReadOnlyRejection() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        RfidWriteService writeService = new RfidWriteService(registry, "hub");

        HeadlessReaderConfig cfg = new HeadlessReaderConfig("read-only-gate");
        cfg.setReadOnly(true);
        HeadlessReaderInstance inst = new HeadlessReaderInstance(cfg, null, null, null);

        TagWriteItem item = new TagWriteItem("E00401001111", "ITEM-1", "001596-0", "CH", 1, 1, 1, "07");
        WriteTagResponseData resp = writeService.writeTagsOnReader(inst, List.of(item), null, null, null, null, false).join();

        assertEquals("error", resp.status());
        assertTrue(resp.error().contains("read-only"));
    }

    @Test
    @DisplayName("Test writeTags executes successfully on reader instance")
    void testWriteTagSuccess() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        RfidWriteService writeService = new RfidWriteService(registry, "hub");

        AtomicBoolean wrote = new AtomicBoolean(false);
        ISO15693Reader reader = new MockISO15693Reader() {
            @Override public void writeTag(String uid, FinnishDataModel model, Byte afi) {
                wrote.set(true);
            }
        };

        HeadlessReaderConfig cfg = new HeadlessReaderConfig("writer-01");
        HeadlessReaderInstance inst = new HeadlessReaderInstance(cfg, reader, null, null);

        TagWriteItem item = new TagWriteItem("E00401001111", "ITEM-1", "001596-0", "CH", 1, 1, 1, "07");
        WriteTagResponseData resp = writeService.writeTagsOnReader(inst, List.of(item), null, null, null, null, false).join();

        assertTrue(wrote.get());
        assertEquals("ok", resp.status());
        assertEquals("E00401001111", resp.uid());
        assertEquals("ITEM-1", resp.itemId());
        assertEquals("07", resp.afi());
    }

    @Test
    @DisplayName("Test writeAfi executes successfully on reader instance")
    void testWriteAfiSuccess() {
        HeadlessReaderRegistry registry = new HeadlessReaderRegistry();
        RfidWriteService writeService = new RfidWriteService(registry, "hub");

        AtomicBoolean wroteAfi = new AtomicBoolean(false);
        ISO15693Reader reader = new MockISO15693Reader() {
            @Override public void writeAfi(String uid, byte afi) {
                wroteAfi.set(true);
                assertEquals((byte) 7, afi);
            }
        };

        HeadlessReaderConfig cfg = new HeadlessReaderConfig("writer-01");
        HeadlessReaderInstance inst = new HeadlessReaderInstance(cfg, reader, null, null);

        WriteAfiResponseData resp = writeService.writeAfiOnReader(inst, List.of("E00401001111"), "07", null, null, null, null, false).join();

        assertTrue(wroteAfi.get());
        assertEquals("ok", resp.status());
        assertEquals("07", resp.afi());
    }
}

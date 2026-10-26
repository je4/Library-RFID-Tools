package org.objectspace.rfid.headless;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.core.TagScanProcessor;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessReaderCallbackTest {

    @Test
    @DisplayName("Test time-based debounce suppresses duplicate scans within window and allows scans after expiration")
    public void testTimeBasedDebounce() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        long debounceWindow = 200L; // 200 ms for fast test
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, debounceWindow);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-001", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);

        String uid1 = "E004010011112222";
        String uid2 = "E004010033334444";

        // 1. First scan of UID1 -> must be dispatched
        callback.doIt(1, 1, "NXP", "SLIX", uid1, data, 4);
        assertEquals(1, callback.getTotalScans());
        assertEquals(1, callback.getDispatchedScans());
        assertEquals(0, callback.getDebouncedScans());

        // 2. Immediate second scan of UID1 -> must be debounced
        callback.doIt(1, 1, "NXP", "SLIX", uid1, data, 4);
        assertEquals(2, callback.getTotalScans());
        assertEquals(1, callback.getDispatchedScans());
        assertEquals(1, callback.getDebouncedScans());

        // 3. Scan of a different UID2 -> must be dispatched
        callback.doIt(1, 1, "NXP", "SLIX", uid2, data, 4);
        assertEquals(3, callback.getTotalScans());
        assertEquals(2, callback.getDispatchedScans());
        assertEquals(1, callback.getDebouncedScans());

        // 4. Wait for debounce window to expire (>200 ms)
        Thread.sleep(250);

        // 5. Scan UID1 again -> must be dispatched
        callback.doIt(1, 1, "NXP", "SLIX", uid1, data, 4);
        assertEquals(4, callback.getTotalScans());
        assertEquals(3, callback.getDispatchedScans());
        assertEquals(1, callback.getDebouncedScans());
    }

    @Test
    @DisplayName("Test Webservice dispatch from HeadlessReaderCallback with omitted/empty marker")
    public void testWebserviceDispatch() throws Exception {
        AtomicInteger dispatchCount = new AtomicInteger(0);
        AtomicReference<String> capturedMarker = new AtomicReference<>();
        AtomicReference<String> capturedUid = new AtomicReference<>();
        AtomicReference<String> capturedItemId = new AtomicReference<>();

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:8080/scan", "POST", "my-secret-key", false);
        WebserviceDispatcher mockDispatcher = new WebserviceDispatcher(wsCfg) {
            @Override
            public WebserviceResponse dispatchScan(
                    String marker,
                    String userText,
                    String nfcContent,
                    String rawPayloadHex,
                    String uid,
                    FinnishDataModel libraryData,
                    String session,
                    String deviceName
            ) {
                dispatchCount.incrementAndGet();
                capturedMarker.set(marker);
                capturedUid.set(uid);
                capturedItemId.set(nfcContent);
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:8080/scan", 5, "my-secret-key", Map.of(), "{}", "");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        TagScanProcessor processor = new TagScanProcessor(cfg) {
            @Override
            public WebserviceDispatcher getWebserviceDispatcher() {
                return mockDispatcher;
            }

            @Override
            public WebserviceResponse dispatchWebservice(String marker, String itemId, String rawHex, String uid,
                    FinnishDataModel metadata, String sessionName, String extra) throws Exception {
                return mockDispatcher.dispatchScan(marker, marker, itemId, rawHex, uid, metadata, sessionName, extra);
            }
        };

        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-999", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);

        String uid = "E004010099998888";
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);

        assertEquals(1, dispatchCount.get(), "Webservice dispatch should have been invoked exactly once");
        assertEquals("", capturedMarker.get(), "Marker should be empty in headless mode");
        assertEquals(uid, capturedUid.get(), "UID should match");
        assertEquals("ITEM-999", capturedItemId.get(), "ItemId should match decoded FinnishDataModel");
    }

    @Test
    @DisplayName("Test cache pruning removes expired entries when map grows beyond limit")
    public void testCachePruning() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        long debounceWindow = 50L;
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, debounceWindow);

        // Populate map with 501 items older than 2x debounce
        long oldTime = System.currentTimeMillis() - 200L;
        for (int i = 0; i < 505; i++) {
            callback.getLastSeenMap().put("UID-" + i, oldTime);
        }
        assertEquals(505, callback.getLastSeenMap().size());

        // Perform one scan to trigger pruning
        FinnishDataModel model = new FinnishDataModel();
        callback.doIt(1, 1, "NXP", "SLIX", "UID-TRIGGER", model.getBlock(32), 4);

        // Old entries should be removed
        assertTrue(callback.getLastSeenMap().size() < 10, "Old cache entries should be pruned");
        assertTrue(callback.getLastSeenMap().containsKey("UID-TRIGGER"));
    }

    @Test
    @DisplayName("Test clearCache removes all tracked tags")
    public void testClearCache() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        callback.getLastSeenMap().put("UID-A", System.currentTimeMillis());
        callback.getLastSeenMap().put("UID-B", System.currentTimeMillis());
        assertEquals(2, callback.getLastSeenMap().size());

        callback.clearCache();
        assertEquals(0, callback.getLastSeenMap().size());

        callback.close();
        assertEquals(0, callback.getLastSeenMap().size());
    }

    @Test
    @DisplayName("Test empty callback method executes without exception and tracks edge transitions")
    public void testEmptyCallback() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        // Initially no active tags
        assertFalse(callback.hasActiveTags());
        assertDoesNotThrow(callback::empty);
        assertEquals(0, callback.getClearedEvents());

        // Scan a tag
        FinnishDataModel model = new FinnishDataModel();
        callback.doIt(1, 1, "NXP", "SLIX", "UID-TEST", model.getBlock(32), 4);
        assertTrue(callback.hasActiveTags());
        assertEquals(1, callback.getLastSeenMap().size());

        // Second immediate scan of same tag without clearing -> debounced
        callback.doIt(1, 1, "NXP", "SLIX", "UID-TEST", model.getBlock(32), 4);
        assertEquals(1, callback.getDispatchedScans());
        assertEquals(1, callback.getDebouncedScans());

        // Empty field (reader cleared) -> resets active tags and clears debounce cache
        callback.empty();
        assertFalse(callback.hasActiveTags());
        assertEquals(1, callback.getClearedEvents());
        assertEquals(0, callback.getLastSeenMap().size(), "Debounce cache should be cleared on reader empty event");

        // Scan the same tag immediately after empty -> must be dispatched again
        callback.doIt(1, 1, "NXP", "SLIX", "UID-TEST", model.getBlock(32), 4);
        assertEquals(2, callback.getDispatchedScans(), "Tag should be dispatched immediately after reader cleared");
        assertEquals(1, callback.getDebouncedScans());
        assertTrue(callback.hasActiveTags());

        // Subsequent empty calls while field remains empty
        callback.empty();
        assertEquals(2, callback.getClearedEvents());
        callback.empty();
        assertEquals(2, callback.getClearedEvents());

        callback.close();
    }

    @Test
    @DisplayName("Test zero debounce window disables suppression and dispatches every scan")
    public void testZeroDebounceWindow() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 0L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-ZERO", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);
        String uid = "E004010099998888";

        // Multiple immediate scans of identical tag
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);

        assertEquals(3, callback.getTotalScans());
        assertEquals(3, callback.getDispatchedScans());
        assertEquals(0, callback.getDebouncedScans());

        callback.close();
    }

    @Test
    @DisplayName("Test active tag presence tracking with sliding TTL and debounce independence")
    public void testActivePresenceTrackingAndTtl() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        long debounceMillis = 1000L;
        long presenceTtlMillis = 200L;
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, null, debounceMillis, presenceTtlMillis);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-PRESENCE", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);
        String uid = "E004010099998888";

        assertFalse(callback.isTagPresent(uid));
        assertEquals(0, callback.getPresentTagCount());

        // 1. Initial scan -> tag is registered as present and dispatched
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertTrue(callback.isTagPresent(uid));
        assertEquals(1, callback.getPresentTagCount());
        assertTrue(callback.getPresentUids().contains(uid));
        assertEquals(1, callback.getDispatchedScans());
        assertEquals(0, callback.getDebouncedScans());

        // 2. Second scan within debounce window (e.g. 50ms later) -> debounced from event dispatch, but presence timestamp updated
        Thread.sleep(50);
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(1, callback.getDispatchedScans());
        assertEquals(1, callback.getDebouncedScans());
        assertTrue(callback.isTagPresent(uid), "Tag must remain present even when dispatch is debounced");
        assertEquals(1, callback.getPresentTagCount());

        // 3. Wait for presence TTL to expire (>200 ms) without new scans
        Thread.sleep(250);
        assertFalse(callback.isTagPresent(uid), "Tag should expire from presence cache after TTL without reads");
        assertEquals(0, callback.getPresentTagCount());

        callback.close();
    }

    @Test
    @DisplayName("Test empty clears presence cache immediately for all tags")
    public void testEmptyClearsPresenceImmediately() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, null, 3000L, 2000L);

        FinnishDataModel model = new FinnishDataModel();
        byte[] data = model.getBlock(32);
        String uid1 = "E004010011112222";
        String uid2 = "E004010033334444";

        callback.doIt(1, 1, "NXP", "SLIX", uid1, data, 4);
        callback.doIt(1, 1, "NXP", "SLIX", uid2, data, 4);

        assertTrue(callback.isTagPresent(uid1));
        assertTrue(callback.isTagPresent(uid2));
        assertEquals(2, callback.getPresentTagCount());

        callback.empty();

        assertFalse(callback.isTagPresent(uid1), "UID1 must not be present after field cleared");
        assertFalse(callback.isTagPresent(uid2), "UID2 must not be present after field cleared");
        assertEquals(0, callback.getPresentTagCount());
        assertTrue(callback.getPresentUids().isEmpty());

        callback.close();
    }

    @Test
    @DisplayName("Test clearDebounceCache resets dispatch cooldown while preserving active tag presence")
    public void testClearDebounceCachePreservesActivePresence() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, null, 5000L, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        byte[] data = model.getBlock(32);
        String uid = "E004010055556666";

        // Initial scan
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(1, callback.getDispatchedScans());
        assertTrue(callback.isTagPresent(uid));

        // Immediate write operation settles -> clear only debounce cache
        callback.clearDebounceCache();

        // Tag must still be actively present for subsequent write operations (e.g. AFI)
        assertTrue(callback.isTagPresent(uid), "Tag must still be present after clearing debounce cache");
        assertEquals(1, callback.getPresentTagCount());

        // Next scan cycle immediately re-dispatches the updated tag metadata
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(2, callback.getDispatchedScans(), "Tag should be re-dispatched immediately after clearDebounceCache");

        callback.close();
    }

    @Test
    @DisplayName("Test RF dropout resilience keeps tag present across brief missing cycles within TTL")
    public void testRfDropoutResilienceWithinPresenceTtl() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);
        long presenceTtl = 400L;
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, null, 2000L, presenceTtl);

        FinnishDataModel model = new FinnishDataModel();
        byte[] data = model.getBlock(32);
        String uid = "E004010077778888";

        // T0: Tag read
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertTrue(callback.isTagPresent(uid));

        // T1 (+150ms): RF glitch / missed scan cycle -> no doIt call
        Thread.sleep(150);
        assertTrue(callback.isTagPresent(uid), "Tag should tolerate brief RF dropouts within TTL");

        // T2 (+250ms total): Tag read again before TTL expires
        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertTrue(callback.isTagPresent(uid));

        // T3 (+300ms since T2): Tag is still present because timestamp was refreshed at T2
        Thread.sleep(200);
        assertTrue(callback.isTagPresent(uid), "Presence should be extended by renewed scan");

        callback.close();
    }

    @Test
    @DisplayName("Test onReaderDisconnected clears presence map, debounce cache, and marks plate not active")
    public void testOnReaderDisconnectedClearsPresenceAndDebounce() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        HeadlessReaderCallback callback = new HeadlessReaderCallback(processor, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-DISCONNECT-1", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);
        String uid = "E0040100ABCD1234";

        callback.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertTrue(callback.hasActiveTags());
        assertTrue(callback.isTagPresent(uid));
        assertTrue(callback.getLastSeenMap().containsKey(uid));
        assertEquals(0, callback.getClearedEvents());

        // Invoke reader disconnected event
        callback.onReaderDisconnected();

        assertFalse(callback.hasActiveTags(), "hasActiveTags must be false after disconnect");
        assertFalse(callback.isTagPresent(uid), "Tag must not be present after disconnect");
        assertFalse(callback.getLastSeenMap().containsKey(uid), "lastSeenMap must be cleared after disconnect");
        assertEquals(1, callback.getClearedEvents(), "Cleared event count must increment");

        // Subsequent disconnect while empty should be idempotent and not trigger duplicate event
        callback.onReaderDisconnected();
        assertEquals(1, callback.getClearedEvents(), "Redundant disconnect must not duplicate cleared event");

        callback.close();
    }

    @Test
    @DisplayName("Test independent debounce caches and plate clearance across isolated reader instances")
    public void testIsolatedReaderCallbacksAndCaches() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);

        HeadlessReaderCallback callback1 = new HeadlessReaderCallback(processor, null, 5000L, 2000L, "reader-01");
        HeadlessReaderCallback callback2 = new HeadlessReaderCallback(processor, null, 5000L, 2000L, "reader-02");

        assertEquals("reader-01", callback1.getReaderId());
        assertEquals("reader-02", callback2.getReaderId());

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-MULTI-1", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);
        String uid = "E004010099990000";

        // Scan tag on Reader 1 -> should dispatch on Reader 1
        callback1.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(1, callback1.getDispatchedScans());
        assertEquals(0, callback1.getDebouncedScans());
        assertTrue(callback1.isTagPresent(uid));

        // Subsequent scan of SAME tag on Reader 1 is debounced by Reader 1
        callback1.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(1, callback1.getDispatchedScans());
        assertEquals(1, callback1.getDebouncedScans());

        // Scan of SAME tag on Reader 2 is NOT debounced by Reader 1's debounce cache!
        assertEquals(0, callback2.getDispatchedScans());
        callback2.doIt(1, 1, "NXP", "SLIX", uid, data, 4);
        assertEquals(1, callback2.getDispatchedScans(), "Reader 2 must immediately dispatch without being debounced by Reader 1");
        assertTrue(callback2.isTagPresent(uid));

        // Clearing plate on Reader 1 only affects Reader 1
        callback1.empty();
        assertFalse(callback1.isTagPresent(uid), "Reader 1 tag must be cleared");
        assertTrue(callback2.isTagPresent(uid), "Reader 2 tag must remain active and unaffected by Reader 1 clearance");

        callback1.close();
        callback2.close();
    }

    @Test
    @DisplayName("Test readerId field serialization in payload classes")
    public void testPayloadReaderIdSerialization() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 1. TagReadPayload
        org.objectspace.rfid.headless.payload.TagReadPayload tagPayload =
                new org.objectspace.rfid.headless.payload.TagReadPayload(
                        "E00401001234", "ITEM-1", "AABB", "001596-0", "CH", true, "SLIX",
                        "sess-1", 1000L, "client-ws", "reader-gate-01", 1, 1, 1, 1, "07"
                );
        String tagJson = mapper.writeValueAsString(tagPayload);
        assertTrue(tagJson.contains("\"readerId\":\"reader-gate-01\""), "JSON must contain readerId: " + tagJson);
        assertTrue(tagJson.contains("\"clientId\":\"client-ws\""), "JSON must contain clientId: " + tagJson);

        // 2. TagsClearedPayload
        org.objectspace.rfid.headless.payload.TagsClearedPayload clearPayload =
                new org.objectspace.rfid.headless.payload.TagsClearedPayload("sess-1", 1000L, "client-ws", "reader-gate-02");
        String clearJson = mapper.writeValueAsString(clearPayload);
        assertTrue(clearJson.contains("\"readerId\":\"reader-gate-02\""), "JSON must contain readerId: " + clearJson);

        // 3. DeviceMetadataPayload
        org.objectspace.rfid.headless.payload.DeviceMetadataPayload metaPayload =
                new org.objectspace.rfid.headless.payload.DeviceMetadataPayload(
                        "52E688", "MR102", "Desc", "sess-1", 1000L, "client-ws", "reader-gate-03"
                );
        String metaJson = mapper.writeValueAsString(metaPayload);
        assertTrue(metaJson.contains("\"readerId\":\"reader-gate-03\""), "JSON must contain readerId: " + metaJson);

        // 4. WriteTagResponsePayload
        org.objectspace.rfid.headless.payload.WriteTagResponsePayload writeTagPayload =
                org.objectspace.rfid.headless.payload.WriteTagResponsePayload.success(
                        "E00401001234", "ITEM-1", "001596-0", "CH", 1, 1, 1, "07",
                        "sess-1", "client-ws", "reader-gate-01"
                );
        String writeTagJson = mapper.writeValueAsString(writeTagPayload);
        assertTrue(writeTagJson.contains("\"readerId\":\"reader-gate-01\""), "JSON must contain readerId: " + writeTagJson);

        // 5. WriteAfiResponsePayload
        org.objectspace.rfid.headless.payload.WriteAfiResponsePayload writeAfiPayload =
                org.objectspace.rfid.headless.payload.WriteAfiResponsePayload.success(
                        "E00401001234", "07", "sess-1", "client-ws", "reader-gate-02"
                );
        String writeAfiJson = mapper.writeValueAsString(writeAfiPayload);
        assertTrue(writeAfiJson.contains("\"readerId\":\"reader-gate-02\""), "JSON must contain readerId: " + writeAfiJson);
    }

    @Test
    @DisplayName("Test tag read and reader cleared handling with custom, default, and null reader ID")
    public void testTagReadWithVariousReaderIds() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        TagScanProcessor processor = new TagScanProcessor(cfg);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BOOK-READER-ID", "CH", "001596-0", new ArrayList<>());
        byte[] data = model.getBlock(32);

        // 1. Explicit readerId with AFI
        HeadlessReaderCallback callbackWithId = new HeadlessReaderCallback(processor, null, 1000L, 1000L, "reader-01");
        assertEquals("reader-01", callbackWithId.getReaderId());
        callbackWithId.doIt(1, 1, "NXP", "SLIX2", "E00401080AFD252F", data, 4, "07");
        assertTrue(callbackWithId.hasActiveTags());
        callbackWithId.empty();
        assertFalse(callbackWithId.hasActiveTags());
        callbackWithId.close();

        // 2. Explicit readerId without AFI
        HeadlessReaderCallback callbackNoAfi = new HeadlessReaderCallback(processor, null, 1000L, 1000L, "reader-02");
        assertEquals("reader-02", callbackNoAfi.getReaderId());
        callbackNoAfi.doIt(1, 1, "NXP", "SLIX2", "E00401080AD4B4E7", data, 4);
        assertTrue(callbackNoAfi.hasActiveTags());
        callbackNoAfi.empty();
        callbackNoAfi.close();

        // 3. Null / blank readerId (legacy single-reader mode)
        HeadlessReaderCallback callbackNullId = new HeadlessReaderCallback(processor, null, 1000L, 1000L, null);
        assertNull(callbackNullId.getReaderId());
        callbackNullId.doIt(1, 1, "NXP", "SLIX2", "E00401080AFD252F", data, 4, "C2");
        assertTrue(callbackNullId.hasActiveTags());
        callbackNullId.empty();
        callbackNullId.close();

        HeadlessReaderCallback callbackBlankId = new HeadlessReaderCallback(processor, null, 1000L, 1000L, "   ");
        assertNull(callbackBlankId.getReaderId());
        callbackBlankId.doIt(1, 1, "NXP", "SLIX2", "E00401080AFD252F", data, 4);
        callbackBlankId.empty();
        callbackBlankId.close();
    }
}

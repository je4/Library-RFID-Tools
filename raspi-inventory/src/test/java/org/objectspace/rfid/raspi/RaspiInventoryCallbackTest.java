package org.objectspace.rfid.raspi;

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

public class RaspiInventoryCallbackTest {

    @Test
    @DisplayName("Test time-based debounce suppresses duplicate scans within window and allows scans after expiration")
    public void testTimeBasedDebounce() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        long debounceWindow = 200L; // 200 ms for fast test
        RaspiInventoryCallback callback = new RaspiInventoryCallback(processor, debounceWindow);

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
    @DisplayName("Test Webservice dispatch from RaspiInventoryCallback with omitted/empty marker")
    public void testWebserviceDispatch() throws Exception {
        AtomicInteger dispatchCount = new AtomicInteger(0);
        AtomicReference<String> capturedMarker = new AtomicReference<>();
        AtomicReference<String> capturedUid = new AtomicReference<>();
        AtomicReference<String> capturedItemId = new AtomicReference<>();

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:8080/raspi/scan", "POST", "my-secret-key", false);
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
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:8080/raspi/scan", 5, "my-secret-key", Map.of(), "{}", "");
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

        RaspiInventoryCallback callback = new RaspiInventoryCallback(processor, 3000L);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "RASPI-ITEM-99", "DE", "ISIL-BERLIN", new ArrayList<>());
        byte[] data = model.getBlock(32);

        callback.doIt(1, 1, "NXP", "SLIX", "E004015077889900", data, 4);

        assertEquals(1, dispatchCount.get());
        assertEquals("", capturedMarker.get(), "Marker should be empty for headless Raspi inventory");
        assertEquals("E004015077889900", capturedUid.get());
        assertEquals("RASPI-ITEM-99", capturedItemId.get());
    }

    @Test
    @DisplayName("Test clearCache and lifecycle methods")
    public void testCacheAndLifecycle() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", false);

        TagScanProcessor processor = new TagScanProcessor(cfg);
        RaspiInventoryCallback callback = new RaspiInventoryCallback(processor, 3000L);

        callback.doIt(1, 1, "NXP", "SLIX", "UID-001", null, 4);
        assertEquals(1, callback.getLastSeenMap().size());

        callback.empty();
        callback.clearCache();
        assertEquals(0, callback.getLastSeenMap().size());

        assertDoesNotThrow(() -> callback.close());
    }

    @Test
    @DisplayName("Test Webservice debug logging emits url, headers, and body at DEBUG level")
    public void testWebserviceDebugLogging() throws Exception {
        ch.qos.logback.classic.Logger callbackLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RaspiInventoryCallback.class);
        ch.qos.logback.classic.Level originalLevel = callbackLogger.getLevel();
        callbackLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        callbackLogger.addAppender(listAppender);

        try {
            Map<String, String> headers = Map.of("Authorization", "Bearer test-jwt", "Content-Type", "application/json");
            String reqUrl = "https://inventory.sammlung.cc/inventory?clientid=1";
            String reqBody = "{\"uid\":\"E00401080AD4B4E7\",\"clientId\":\"1\"}";

            WebserviceConfig wsCfg = new WebserviceConfig(true, reqUrl, "POST", "my-secret-key", false);
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
                    return WebserviceResponse.success(200, "{\"ok\":true}", reqUrl, 520, "my-secret-key", headers, reqBody, "");
                }
            };

            BaseConfiguration cfg = new BaseConfiguration();
            cfg.setProperty("database.active", false);
            cfg.setProperty("webservice.active", true);

            TagScanProcessor processor = new TagScanProcessor(cfg) {
                @Override
                public WebserviceResponse dispatchWebservice(String marker, String itemId, String rawHex, String uid,
                        FinnishDataModel metadata, String sessionName, String extra) throws Exception {
                    return mockDispatcher.dispatchScan(marker, marker, itemId, rawHex, uid, metadata, sessionName, extra);
                }
            };

            RaspiInventoryCallback callback = new RaspiInventoryCallback(processor, 3000L);
            callback.doIt(1, 1, "NXP", "SLIX", "E00401080AD4B4E7", null, 4);

            boolean foundUrl = false;
            boolean foundHeaders = false;
            boolean foundBody = false;

            for (ch.qos.logback.classic.spi.ILoggingEvent event : listAppender.list) {
                if (event.getLevel() == ch.qos.logback.classic.Level.DEBUG) {
                    String msg = event.getFormattedMessage();
                    if (msg.contains("URL: " + reqUrl)) {
                        foundUrl = true;
                    }
                    if (msg.contains("Headers:") && msg.contains("Authorization") && msg.contains("Bearer test-jwt")) {
                        foundHeaders = true;
                    }
                    if (msg.contains("Body: " + reqBody)) {
                        foundBody = true;
                    }
                }
            }

            assertTrue(foundUrl, "Debug log should contain URL");
            assertTrue(foundHeaders, "Debug log should contain headers");
            assertTrue(foundBody, "Debug log should contain body");
        } finally {
            callbackLogger.detachAppender(listAppender);
            callbackLogger.setLevel(originalLevel);
        }
    }
}

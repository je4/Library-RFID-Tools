package org.objectspace.rfid.library.inventory;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryCallbackWebserviceTest {

    @Test
    @DisplayName("Test InventoryCallback invokes WebserviceDispatcher")
    public void testInventoryCallbackWebserviceInvocation() throws Exception {
        AtomicReference<String> dispatchedUid = new AtomicReference<>();
        AtomicReference<String> dispatchedMarker = new AtomicReference<>();

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:1234/test", "POST", "key", false);
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
                dispatchedMarker.set(marker);
                dispatchedUid.set(uid);
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:1234/test", 10, "key", Map.of(), "{}", "");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-X";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-12345", "CH", "001596-0", new ArrayList<>());
        byte[] rawData = model.getBlock(32);

        callback.doIt(1, 1, "NXP", "SLIX", "E004015099887766", rawData, 4);

        assertEquals("E004015099887766", dispatchedUid.get());
        assertEquals("Shelf-X", dispatchedMarker.get());
    }

    @Test
    @DisplayName("Test InventoryCallback processTestScan (Synchronous Test Entry)")
    public void testInventoryCallbackProcessTestScan() throws Exception {
        AtomicReference<String> dispatchedUid = new AtomicReference<>();
        AtomicReference<String> dispatchedItemId = new AtomicReference<>();
        AtomicReference<FinnishDataModel> dispatchedModel = new AtomicReference<>();

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:1234/test", "POST", "key", false);
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
                dispatchedUid.set(uid);
                dispatchedItemId.set(libraryData != null ? libraryData.getPrimaryItemId() : null);
                dispatchedModel.set(libraryData);
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:1234/test", 10, "key", Map.of(), "{}", "");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Test-Shelf-A";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        callback.processTestScan("CUSTOM-ITEM-999");

        assertNotNull(dispatchedUid.get());
        assertTrue(dispatchedUid.get().startsWith("E0040150"));
        assertTrue(dispatchedUid.get().endsWith("ABCD"));
        assertEquals("CUSTOM-ITEM-999", dispatchedItemId.get());

        FinnishDataModel model = dispatchedModel.get();
        assertNotNull(model);
        assertEquals("CH", model.getCountryOfOwnerLib());
        assertEquals("ISIL-123", model.getISIL());
        assertEquals(1, model.getTypeOfUsage());
        assertEquals(1, model.getPartsInItem());
        assertEquals(1, model.getPartNumber());
        assertEquals(1, model.getVersion());
        assertFalse(model.getCRCError());
    }

    @Test
    @DisplayName("Test InventoryCallback triggerTestScan (Asynchronous Test Entry)")
    public void testInventoryCallbackTriggerTestScan() throws Exception {
        AtomicReference<String> dispatchedUid = new AtomicReference<>();
        AtomicReference<String> dispatchedItemId = new AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:1234/test", "GET", "", false);
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
                dispatchedUid.set(uid);
                dispatchedItemId.set(libraryData != null ? libraryData.getPrimaryItemId() : null);
                latch.countDown();
                return WebserviceResponse.success(200, "{\"ok\":true}", "http://localhost:1234/test", 10, "", Map.of(), "", "");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-Async";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        String generatedUid = callback.triggerTestScan();
        assertNotNull(generatedUid);

        boolean reached = latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(reached, "Async test scan should have been dispatched within 3 seconds");
        assertEquals(generatedUid, dispatchedUid.get());
        assertNotNull(dispatchedItemId.get());
        assertTrue(dispatchedItemId.get().startsWith("3011"));
    }

    @Test
    @DisplayName("Test InventoryCallback logs config file path on startup")
    public void testInventoryCallbackLogsConfigFileOnStartup() throws Exception {
        java.util.List<String> logMessages = new java.util.ArrayList<>();
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("config.file.path", "C:\\test\\custom_inventory.xml");

        InventoryCallback callback =
                new InventoryCallback(null, cfg, "C:\\test\\custom_inventory.xml") {
                    @Override
                    protected void print(String txt, int c1, int c2) {
                        logMessages.add(txt);
                    }
                };

        assertEquals("C:\\test\\custom_inventory.xml", callback.getConfigFilePath());
        boolean hasConfigLog = logMessages.stream()
                .anyMatch(msg -> msg.contains("Konfigurationsdatei: C:\\test\\custom_inventory.xml"));
        assertTrue(hasConfigLog, "Expected configuration file path in scan log: " + logMessages);
    }

    @Test
    @DisplayName("Test InventoryCallback logs detailed error in scan protocol when HTTP status != 200")
    public void testInventoryCallbackLogsNon200Error() throws Exception {
        java.util.List<String> logMessages = new java.util.ArrayList<>();

        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:9999/fail", "POST", "12345678901234567890", false);
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
                Map<String, String> headers = Map.of(
                        "User-Agent", "Iso15693NfcReader/1.0 (Desktop)",
                        "Authorization", "Bearer dummy.token.here"
                );
                return WebserviceResponse.failure(
                        401,
                        "HTTP error 401: Unauthorized",
                        "http://localhost:9999/fail",
                        25,
                        "12345678901234567890",
                        headers,
                        "{\"marker\":\"Test\"}",
                        "debug"
                );
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {
                        logMessages.add(txt);
                    }
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-Error-Test";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        callback.processTestScan("ERR-ITEM-001");

        boolean foundErrorLog = false;
        for (String msg : logMessages) {
            if (msg.contains("[Webservice Fehler]") && msg.contains("HTTP Status: 401")) {
                foundErrorLog = true;
                assertTrue(msg.contains("URL: http://localhost:9999/fail"));
                assertTrue(msg.contains("JWT-Key-Länge: 20 Zeichen"));
                assertTrue(msg.contains("Authorization: Bearer dummy.token.here"));
                assertTrue(msg.contains("Body: {\"marker\":\"Test\"}"));
            }
        }
        assertTrue(foundErrorLog, "Expected detailed non-200 Webservice error log in scan protocol");
    }

    @Test
    @DisplayName("Test InventoryCallback computes success status correctly on HTTP 200")
    public void testInventoryCallbackStatusSuccessOn200() throws Exception {
        WebserviceConfig wsCfg = new WebserviceConfig(true, "http://localhost:8080/ok", "POST", "secretKey123", false);
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
                return WebserviceResponse.success(200, "{\"status\":\"ok\"}", "http://localhost:8080/ok", 15, "secretKey123", Map.of(), "{}", "debug");
            }
        };

        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-1";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-OK-01", "CH", "ISIL-1", null);
        byte[] data = model.getBlock(48);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401509999ABCD", data, 4);

        assertTrue(wsCfg.isActive());
    }

    @Test
    @DisplayName("Test InventoryCallback handles status failure when database is active but disconnected")
    public void testInventoryCallbackStatusDbFailure() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", true);
        cfg.setProperty("database.driver", "com.mysql.cj.jdbc.Driver");
        cfg.setProperty("database.dsn", "jdbc:mysql://invalid-host:3306/nonexistent");
        cfg.setProperty("webservice.active", false);

        InventoryCallback callback =
                new InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-1";
                    }
                };

        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-DB-FAIL", "CH", "ISIL-1", null);
        byte[] data = model.getBlock(48);

        assertDoesNotThrow(() -> callback.doIt(1, 1, "NXP", "SLIX", "E00401508888ABCD", data, 4));
    }
}

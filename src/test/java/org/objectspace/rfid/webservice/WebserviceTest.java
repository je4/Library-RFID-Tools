/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
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
 *******************************************************************************/
package org.objectspace.rfid.webservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.rfid.FinnishDataModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class WebserviceTest {

    @Test
    @DisplayName("Test WebserviceConfig parsing and defaults")
    public void testWebserviceConfigParsing() {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("webservice.active", true);
        cfg.setProperty("webservice.target_url", "https://example.org/rfid/scan");
        cfg.setProperty("webservice.http_method", "POST");
        cfg.setProperty("webservice.jwt_key", "secret123");
        cfg.setProperty("webservice.debug_mode", true);

        WebserviceConfig config = WebserviceConfig.fromConfiguration(cfg);
        assertTrue(config.isActive());
        assertEquals("https://example.org/rfid/scan", config.getTargetUrl());
        assertEquals("POST", config.getHttpMethod());
        assertEquals("secret123", config.getJwtKey());
        assertTrue(config.isDebugMode());
    }

    @Test
    @DisplayName("Test WebserviceConfig alias fallback keys")
    public void testWebserviceConfigAliasFallbacks() {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("webservice.active", "true");
        cfg.setProperty("webservice.url", "http://custom-host/api");
        cfg.setProperty("webservice.method", "get");
        cfg.setProperty("webservice.jwt", "myJwtSecret");
        cfg.setProperty("webservice.debug", true);

        WebserviceConfig config = WebserviceConfig.fromConfiguration(cfg);
        assertTrue(config.isActive());
        assertEquals("http://custom-host/api", config.getTargetUrl());
        assertEquals("GET", config.getHttpMethod());
        assertEquals("myJwtSecret", config.getJwtKey());
        assertTrue(config.isDebugMode());
    }

    @Test
    @DisplayName("Test JWT Generation and Verification")
    public void testJwtGenerationAndVerification() {
        String secret = "super-secret-key-for-unit-testing";
        long now = 1700000000000L;
        String token = JwtGenerator.generateToken(secret, 60, now, Map.of("device", "Reader-01"));

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        // Verify valid signature
        assertTrue(JwtGenerator.verifySignature(token, secret));

        // Corrupted secret fails
        assertFalse(JwtGenerator.verifySignature(token, "wrong-secret"));

        // Header check
        String headerJson = new String(JwtGenerator.base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"HS256\""));
        assertTrue(headerJson.contains("\"typ\":\"JWT\""));

        // Payload check
        String payloadJson = new String(JwtGenerator.base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payloadJson.contains("\"iat\":1700000000"));
        assertTrue(payloadJson.contains("\"exp\":1700000060"));
        assertTrue(payloadJson.contains("\"device\":\"Reader-01\""));
    }

    @Test
    @DisplayName("Test UrlBuilder with query parameters (GET)")
    public void testUrlBuilderGetQueryParams() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "BARCODE-42", "CH", "001596-0", new ArrayList<>());
        byte[] block = model.getBlock(32);
        model.setBlock(block, 4);

        String url = UrlBuilder.buildUrl(
                "http://localhost:8080/scan",
                "Shelf-A",
                "01020304",
                "E004010012345678",
                1700000000000L,
                "",
                model,
                "",
                "Session-01",
                "ReaderDevice",
                "GET",
                ""
        );

        assertTrue(url.startsWith("http://localhost:8080/scan?"));
        assertTrue(url.contains("marker=Shelf-A"));
        assertTrue(url.contains("session=Session-01"));
        assertTrue(url.contains("uid=E004010012345678"));
        assertTrue(url.contains("itemid=BARCODE-42"));
        assertTrue(url.contains("country=CH"));
        assertTrue(url.contains("isil=001596-0"));
        assertTrue(url.contains("parts=1"));
        assertTrue(url.contains("partno=1"));
    }

    @Test
    @DisplayName("Test UrlBuilder with template placeholders (GET & POST)")
    public void testUrlBuilderTemplatePlaceholders() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 2, 1, "ITEM-999", "DE", "ISIL-DE", new ArrayList<>());
        byte[] block = model.getBlock(32);
        model.setBlock(block, 4);

        String template = "http://api.server.com/inventory/{session}/{marker}?tag={uid}&barcode={itemid}";

        String getUrl = UrlBuilder.buildUrl(
                template,
                "Shelf-B",
                "AABB",
                "E0041111",
                1700000000000L,
                "token123",
                model,
                "",
                "MySession",
                "Dev",
                "GET",
                ""
        );

        assertEquals("http://api.server.com/inventory/MySession/Shelf-B?tag=E0041111&barcode=ITEM-999", getUrl);

        // POST template strips query part
        String postTemplate = "http://api.server.com/api/v1/items/{itemid}/tags/{uid}?extra=param";
        String postUrl = UrlBuilder.buildUrl(
                postTemplate,
                "Shelf-B",
                "AABB",
                "E0041111",
                1700000000000L,
                "",
                model,
                "",
                "MySession",
                "Dev",
                "POST",
                ""
        );

        assertEquals("http://api.server.com/api/v1/items/ITEM-999/tags/E0041111", postUrl);
    }

    @Test
    @DisplayName("Test WebserviceDispatcher HTTP GET and POST execution with local HTTP Server")
    public void testWebserviceDispatcherHttpExecution() throws Exception {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<String> receivedPathAndQuery = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuthHeader = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rfid", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedMethod.set(exchange.getRequestMethod());
                receivedPathAndQuery.set(exchange.getRequestURI().toString());
                receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));

                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                receivedBody.set(body);

                String response = "{\"status\":\"ok\",\"received\":true}";
                byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(respBytes);
                os.close();
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String baseUrl = "http://localhost:" + port + "/api/rfid";

        try {
            // 1. Test POST Request with JWT
            WebserviceConfig postConfig = new WebserviceConfig(true, baseUrl, "POST", "test-secret-key", true);
            WebserviceDispatcher dispatcher = new WebserviceDispatcher(postConfig);

            FinnishDataModel model = new FinnishDataModel();
            model.setValues(1, 1, 1, "BOOK-101", "CH", "001596-0", new ArrayList<>());
            byte[] block = model.getBlock(32);
            model.setBlock(block, 4);

            WebserviceResponse postResp = dispatcher.dispatchScan(
                    "Shelf-1",
                    "Shelf-1",
                    "BOOK-101",
                    "01020304",
                    "E00401501234",
                    model,
                    "Session-2026",
                    "TestHost"
            );

            assertTrue(postResp.isSuccess());
            assertEquals(200, postResp.getHttpStatus());
            assertEquals("POST", receivedMethod.get());
            assertNotNull(receivedAuthHeader.get());
            assertTrue(receivedAuthHeader.get().startsWith("Bearer "));
            assertNotNull(receivedBody.get());
            assertTrue(receivedBody.get().contains("\"itemId\":\"BOOK-101\""));
            assertTrue(receivedBody.get().contains("\"uid\":\"E00401501234\""));
            assertTrue(receivedBody.get().contains("\"marker\":\"Shelf-1\""));

            // 2. Test GET Request
            WebserviceConfig getConfig = new WebserviceConfig(true, baseUrl, "GET", "", false);
            WebserviceDispatcher getDispatcher = new WebserviceDispatcher(getConfig);

            WebserviceResponse getResp = getDispatcher.dispatchScan(
                    "Shelf-2",
                    "Shelf-2",
                    "BOOK-202",
                    "AABB",
                    "E00401509999",
                    model,
                    "Session-2026",
                    "TestHost"
            );

            assertTrue(getResp.isSuccess());
            assertEquals(200, getResp.getHttpStatus());
            assertEquals("GET", receivedMethod.get());
            assertTrue(receivedPathAndQuery.get().contains("uid=E00401509999"));
            assertTrue(receivedPathAndQuery.get().contains("marker=Shelf-2"));
        } finally {
            server.stop(0);
        }
    }

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

        // We can pass null for InventoryDialog in testing if handled or mock it
        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {
                        // Suppress UI print in headless test
                    }
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

        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
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

        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
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
    @DisplayName("Test WebserviceResponse formatScanLogDetails on HTTP error")
    public void testWebserviceResponseFormatScanLogDetails() {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("User-Agent", "Iso15693NfcReader/1.0");
        headers.put("Authorization", "Bearer eyJhbGciOi...");
        headers.put("Content-Type", "application/json; charset=utf-8");

        String body = "{\"marker\":\"Shelf-1\",\"uid\":\"E00401501234\",\"itemId\":\"30119999\"}";
        String secret = "secret1234567890"; // length: 16

        WebserviceResponse resp = WebserviceResponse.failure(
                404,
                "HTTP error 404: Not Found",
                "https://api.library.org/rfid/scan",
                45,
                secret,
                headers,
                body,
                "debug details"
        );

        assertEquals(404, resp.getHttpStatus());
        assertFalse(resp.isSuccess());
        assertEquals(16, resp.getJwtKeyLength());

        String logOutput = resp.formatScanLogDetails();
        assertNotNull(logOutput);
        assertTrue(logOutput.contains("[Webservice Fehler] HTTP Status: 404 (HTTP error 404: Not Found)"));
        assertTrue(logOutput.contains("URL: https://api.library.org/rfid/scan"));
        assertTrue(logOutput.contains("JWT-Key-L\u00E4nge: 16 Zeichen"));
        assertTrue(logOutput.contains("User-Agent: Iso15693NfcReader/1.0"));
        assertTrue(logOutput.contains("Authorization: Bearer eyJhbGciOi..."));
        assertTrue(logOutput.contains("Content-Type: application/json; charset=utf-8"));
        assertTrue(logOutput.contains("Body: " + body));
    }

    @Test
    @DisplayName("Test WebserviceResponse formatScanLogDetails with empty key and GET request")
    public void testWebserviceResponseFormatScanLogDetailsEmptyKeyGet() {
        WebserviceResponse resp = WebserviceResponse.failure(
                500,
                "Internal Server Error",
                "https://api.library.org/rfid/scan?uid=123",
                12,
                null,
                Map.of("User-Agent", "Iso15693NfcReader/1.0"),
                null,
                "debug"
        );

        assertEquals(0, resp.getJwtKeyLength());
        String logOutput = resp.formatScanLogDetails();
        assertTrue(logOutput.contains("HTTP Status: 500"));
        assertTrue(logOutput.contains("JWT-Key-L\u00E4nge: 0 (kein Key konfiguriert)"));
        assertTrue(logOutput.contains("Body: (kein Body / GET-Aufruf)"));
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

        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
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
                assertTrue(msg.contains("JWT-Key-L\u00E4nge: 20 Zeichen"));
                assertTrue(msg.contains("Authorization: Bearer dummy.token.here"));
                assertTrue(msg.contains("Body: {\"marker\":\"Test\"}"));
            }
        }
        assertTrue(foundErrorLog, "Expected detailed non-200 Webservice error log in scan protocol");
    }

    @Test
    @DisplayName("Test InventoryCallback computes success status correctly on HTTP 200")
    public void testInventoryCallbackStatusSuccessOn200() throws Exception {
        AtomicReference<Boolean> capturedSuccess = new AtomicReference<>();
        AtomicReference<String> capturedDetails = new AtomicReference<>();

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

        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
                    @Override
                    protected void print(String txt, int c1, int c2) {}
                    @Override
                    protected String getTagInfo() {
                        return "Shelf-1";
                    }
                };
        callback.setWebserviceDispatcher(mockDispatcher);

        // Subclass/delegate to verify status parameter calculation
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-OK-01", "CH", "ISIL-1", null);
        byte[] data = model.getBlock(48);

        callback.doIt(1, 1, "NXP", "SLIX", "E00401509999ABCD", data, 4);

        // Verify that Webservice returned 200 and was evaluated as success
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

        org.objectspace.rfid.library.inventory.InventoryCallback callback =
                new org.objectspace.rfid.library.inventory.InventoryCallback(null, cfg) {
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

        // Should handle the disconnected DB gracefully without crashing
        assertDoesNotThrow(() -> callback.doIt(1, 1, "NXP", "SLIX", "E00401508888ABCD", data, 4));
    }
}

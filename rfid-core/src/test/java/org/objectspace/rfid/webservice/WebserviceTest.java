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
        cfg.setProperty("webservice.client_id", "1");

        WebserviceConfig config = WebserviceConfig.fromConfiguration(cfg);
        assertTrue(config.isActive());
        assertEquals("https://example.org/rfid/scan", config.getTargetUrl());
        assertEquals("POST", config.getHttpMethod());
        assertEquals("secret123", config.getJwtKey());
        assertTrue(config.isDebugMode());
        assertEquals("1", config.getClientId());
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
        cfg.setProperty("client_id", "station-42");

        WebserviceConfig config = WebserviceConfig.fromConfiguration(cfg);
        assertTrue(config.isActive());
        assertEquals("http://custom-host/api", config.getTargetUrl());
        assertEquals("GET", config.getHttpMethod());
        assertEquals("myJwtSecret", config.getJwtKey());
        assertTrue(config.isDebugMode());
        assertEquals("station-42", config.getClientId());
    }

    @Test
    @DisplayName("Test JWT Generation and Verification")
    public void testJwtGenerationAndVerification() {
        String secret = "super-secret-key-for-unit-testing";
        long now = 1700000000000L;
        String token = JwtGenerator.generateToken(secret, "E00401501234", 60, now, Map.of("client_id", "station-1", "device", "Reader-01"));

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
        assertTrue(payloadJson.contains("\"sub\":\"E00401501234\""));
        assertTrue(payloadJson.contains("\"client_id\":\"station-1\""));
        assertTrue(payloadJson.contains("\"device\":\"Reader-01\""));

        // Simple overload with subject
        String subToken = JwtGenerator.generateToken(secret, "TAG-UID-999");
        assertTrue(JwtGenerator.verifySignature(subToken, secret));
        String subPayload = new String(JwtGenerator.base64UrlDecode(subToken.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(subPayload.contains("\"sub\":\"TAG-UID-999\""));
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

        // POST template with query parameter and placeholder
        String postTemplate = "http://api.server.com/api/v1/items/{itemid}/tags/{uid}?extra=param&client={clientid}";
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
                "",
                "1"
        );

        assertEquals("http://api.server.com/api/v1/items/ITEM-999/tags/E0041111?extra=param&client=1", postUrl);
    }

    @Test
    @DisplayName("Test UrlBuilder clientId parameter appending and placeholder replacement")
    public void testUrlBuilderClientIdHandling() throws Exception {
        FinnishDataModel model = new FinnishDataModel();
        model.setValues(1, 1, 1, "ITEM-100", "CH", "001596-0", new ArrayList<>());

        // 1. GET with automatic clientId appending
        String getUrl = UrlBuilder.buildUrl(
                "http://localhost:8080/scan",
                "Shelf-1",
                "01020304",
                "E004010012345678",
                1700000000000L,
                "",
                model,
                "",
                "Session-01",
                "ReaderDevice",
                "GET",
                "",
                "station-1"
        );
        assertTrue(getUrl.contains("clientid=station-1"));

        // 2. GET with placeholder {clientid}
        String getTemplateUrl = UrlBuilder.buildUrl(
                "http://localhost:8080/scan?client={clientid}&uid={uid}",
                "Shelf-1",
                "01020304",
                "E004010012345678",
                1700000000000L,
                "",
                model,
                "",
                "Session-01",
                "ReaderDevice",
                "GET",
                "",
                "station-1"
        );
        assertEquals("http://localhost:8080/scan?client=station-1&uid=E004010012345678", getTemplateUrl);

        // 3. POST with automatic clientId appending when URL has no query params
        String postUrl = UrlBuilder.buildUrl(
                "https://inventory.sammlung.cc/inventory",
                "Shelf-1",
                "01020304",
                "E004010012345678",
                1700000000000L,
                "",
                model,
                "",
                "Session-01",
                "ReaderDevice",
                "POST",
                "",
                "1"
        );
        assertEquals("https://inventory.sammlung.cc/inventory?clientid=1", postUrl);

        // 4. POST with explicit query parameter in URL
        String postExplicitUrl = UrlBuilder.buildUrl(
                "https://inventory.sammlung.cc/inventory?clientid=1",
                "Shelf-1",
                "01020304",
                "E004010012345678",
                1700000000000L,
                "",
                model,
                "",
                "Session-01",
                "ReaderDevice",
                "POST",
                "",
                "1"
        );
        assertEquals("https://inventory.sammlung.cc/inventory?clientid=1", postExplicitUrl);
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
            // 1. Test POST Request with JWT and clientId
            WebserviceConfig postConfig = new WebserviceConfig(true, baseUrl, "POST", "test-secret-key", true, "client-station-1");
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
            assertTrue(receivedPathAndQuery.get().contains("clientid=client-station-1"));
            assertNotNull(receivedAuthHeader.get());
            assertTrue(receivedAuthHeader.get().startsWith("Bearer "));

            // Verify JWT in Authorization header
            String bearerJwt = receivedAuthHeader.get().substring("Bearer ".length()).trim();
            assertTrue(JwtGenerator.verifySignature(bearerJwt, "test-secret-key"));
            String jwtPayload = new String(JwtGenerator.base64UrlDecode(bearerJwt.split("\\.")[1]), StandardCharsets.UTF_8);
            assertTrue(jwtPayload.contains("\"sub\":\"E00401501234\""));
            assertTrue(jwtPayload.contains("\"client_id\":\"client-station-1\""));

            assertNotNull(receivedBody.get());
            assertTrue(receivedBody.get().contains("\"itemId\":\"BOOK-101\""));
            assertTrue(receivedBody.get().contains("\"uid\":\"E00401501234\""));
            assertTrue(receivedBody.get().contains("\"marker\":\"Shelf-1\""));
            assertTrue(receivedBody.get().contains("\"clientId\":\"client-station-1\""));

            // 2. Test GET Request
            WebserviceConfig getConfig = new WebserviceConfig(true, baseUrl, "GET", "", false, "client-station-2");
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
            assertTrue(receivedPathAndQuery.get().contains("clientid=client-station-2"));
        } finally {
            server.stop(0);
        }
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
        assertTrue(logOutput.contains("JWT-Key-Länge: 16 Zeichen"));
        assertTrue(logOutput.contains("User-Agent: Iso15693NfcReader/1.0"));
        assertTrue(logOutput.contains("Authorization: Bearer eyJhbGciOi..."));
        assertTrue(logOutput.contains("Content-Type: application/json; charset=utf-8"));
        assertTrue(logOutput.contains("Body: " + body));
        assertTrue(logOutput.contains("Rückgabe (HTTP): 404"));
        assertTrue(logOutput.contains("Rückgabe (Body): (kein Response-Body)"));
    }

    @Test
    @DisplayName("Test WebserviceResponse formatScanLogDetails with non-empty response body")
    public void testWebserviceResponseFormatScanLogDetailsWithResponseBody() {
        String respBody = "{\"error\":\"Item not found in catalog\",\"code\":404}";
        WebserviceResponse resp = WebserviceResponse.failure(
                404,
                respBody,
                "HTTP error 404: " + respBody,
                "https://api.library.org/rfid/scan",
                50,
                "mysecret",
                Map.of("Content-Type", "application/json"),
                "{\"itemId\":\"12345\"}",
                "debug"
        );

        String logOutput = resp.formatScanLogDetails();
        assertTrue(logOutput.contains("[Webservice Fehler] HTTP Status: 404"));
        assertTrue(logOutput.contains("Rückgabe (HTTP): 404"));
        assertTrue(logOutput.contains("Rückgabe (Body): " + respBody));
        assertEquals(respBody, resp.getResponseBody());
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
        assertTrue(logOutput.contains("JWT-Key-Länge: 0 (kein Key konfiguriert)"));
        assertTrue(logOutput.contains("Body: (kein Body / GET-Aufruf)"));
    }

    @Test
    @DisplayName("Test TagScanProcessor initialization and configuration")
    public void testTagScanProcessor() throws Exception {
        BaseConfiguration cfg = new BaseConfiguration();
        cfg.setProperty("database.active", false);
        cfg.setProperty("webservice.active", true);
        cfg.setProperty("webservice.target_url", "http://localhost:1234/test");
        cfg.setProperty("webservice.http_method", "POST");
        cfg.setProperty("webservice.jwt_key", "my-test-jwt-key");
        cfg.setProperty("webservice.client_id", "station-1");

        try (org.objectspace.rfid.core.TagScanProcessor processor = new org.objectspace.rfid.core.TagScanProcessor(cfg)) {
            assertFalse(processor.isDatabaseConfigured());
            assertFalse(processor.isDatabaseConnected());
            assertTrue(processor.isWebserviceActive());
            assertNotNull(processor.getWebserviceDispatcher());
            assertEquals("POST", processor.getWebserviceConfig().getHttpMethod());
            assertEquals("station-1", processor.getWebserviceConfig().getClientId());
        }
    }

    @Test
    @DisplayName("Test inventory.xml configuration files contain clientId and updated targetUrl")
    public void testInventoryXmlFilesConfiguration() throws Exception {
        java.io.File[] configFiles = new java.io.File[] {
                new java.io.File("raspi-inventory/inventory.xml"),
                new java.io.File("rfid-inventory/inventory.xml"),
                new java.io.File("inventory.xml")
        };

        for (java.io.File file : configFiles) {
            if (file.exists()) {
                org.apache.commons.configuration2.builder.fluent.Parameters params = new org.apache.commons.configuration2.builder.fluent.Parameters();
                org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder<org.apache.commons.configuration2.XMLConfiguration> builder =
                        new org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder<>(org.apache.commons.configuration2.XMLConfiguration.class)
                                .configure(params.xml().setFile(file));
                org.apache.commons.configuration2.XMLConfiguration xmlConfig = builder.getConfiguration();
                WebserviceConfig wsCfg = WebserviceConfig.fromConfiguration(xmlConfig);
                assertEquals("1", wsCfg.getClientId(), "File " + file.getPath() + " should have client_id=1");
                assertTrue(wsCfg.getTargetUrl().contains("clientid="), "File " + file.getPath() + " target_url should contain clientid: " + wsCfg.getTargetUrl());
            }
        }
    }
}

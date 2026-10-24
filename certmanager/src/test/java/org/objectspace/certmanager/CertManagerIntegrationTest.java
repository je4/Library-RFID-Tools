package org.objectspace.certmanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.client.MiniVaultClient;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.certmanager.config.MiniVaultConfig;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.certmanager.loader.MiniVaultLoader;
import org.objectspace.certmanager.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Integration Test for {@link CertManager} using the real configuration from {@code inventory.xml}.
 */
public class CertManagerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CertManagerIntegrationTest.class);

    private static File findInventoryXml() {
        File[] candidates = new File[]{
                new File("inventory.xml"),
                new File("../inventory.xml"),
                new File("rfid-inventory/inventory.xml"),
                new File("../rfid-inventory/inventory.xml")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("inventory.xml not found in search paths");
    }

    private static boolean isMiniVaultReachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(uri.getHost(), uri.getPort()), 1500);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Test parsing <clienttls> configuration directly from inventory.xml")
    public void testParseConfigurationFromInventoryXml() throws Exception {
        File inventoryXml = findInventoryXml();
        assertTrue(inventoryXml.exists(), "inventory.xml file must exist");

        log.info("Loading configuration from: {}", inventoryXml.getAbsolutePath());
        CertManagerConfig config = CertManager.loadConfigFromFile(inventoryXml);

        assertNotNull(config, "Parsed CertManagerConfig must not be null");
        assertEquals(LoaderType.MINIVAULT, config.getType(), "Loader type must be MINIVAULT");

        MiniVaultConfig vaultConfig = config.getVault();
        assertNotNull(vaultConfig, "MiniVaultConfig must not be null");
        assertEquals("https://localhost:8759/api/v1", vaultConfig.getBaseUrl());
        assertEquals("pa.22f09dfd1cbfbe9f.c17b29cb2b730533", vaultConfig.getParentToken());
        assertEquals("client_cert", vaultConfig.getTokenType());
        assertEquals("client_cert", vaultConfig.getCertType());
        assertFalse(vaultConfig.isUseSystemPool(), "useSystemPool should be false per inventory.xml");

        assertThat(vaultConfig.getTokenPolicies()).contains("wsbroker.client");
        assertThat(vaultConfig.getUris()).contains(
                "wsbroker:client:test-java",
                "wsbroker:channel:all",
                "wsbroker:channel:intern"
        );
        assertThat(vaultConfig.getCa()).contains("certs/minivault.ca.pem");
    }

    @Test
    @DisplayName("Test full MiniVault integration lifecycle and live mTLS HTTP communication using inventory.xml")
    public void testMiniVaultLiveIntegrationWithInventoryXml() throws Exception {
        File inventoryXml = findInventoryXml();
        CertManagerConfig config = CertManager.loadConfigFromFile(inventoryXml);
        MiniVaultConfig vaultConfig = config.getVault();

        if (!isMiniVaultReachable(vaultConfig.getBaseUrl())) {
            log.warn("MiniVault server not reachable at '{}'. Skipping live integration test.", vaultConfig.getBaseUrl());
            return;
        }

        log.info("Starting CertManager client loader with configuration from inventory.xml...");
        try (CertLoader loader = CertManager.createClientLoader(config)) {
            assertNotNull(loader, "CertLoader instance must not be null");
            assertTrue(loader instanceof MiniVaultLoader, "Loader must be instance of MiniVaultLoader");
            MiniVaultLoader mvLoader = (MiniVaultLoader) loader;

            // 1. Verify Active Child Token
            String activeToken = mvLoader.getActiveToken();
            assertNotNull(activeToken, "Active child token must have been issued by MiniVault");
            assertTrue(activeToken.startsWith("cc.") || activeToken.length() > 5, "Child token should have valid format");
            log.info("Successfully obtained active child token: {}", activeToken);

            // 2. Verify Issued Client Certificate & Private Key
            KeyCertPair keyCertPair = loader.getCurrentCertificate();
            assertNotNull(keyCertPair, "KeyCertPair must be populated");
            assertNotNull(keyCertPair.getPrivateKey(), "Private key must be present");
            assertNotNull(keyCertPair.getCertificate(), "Client X.509 certificate must be present");

            X509Certificate cert = keyCertPair.getCertificate();
            log.info("Issued client certificate Subject DN: {}", cert.getSubjectX500Principal().getName());
            log.info("Issued client certificate Issuer DN: {}", cert.getIssuerX500Principal().getName());
            log.info("Certificate validity: {} to {}", cert.getNotBefore(), cert.getNotAfter());

            // 3. Verify CA Bundle
            List<X509Certificate> caList = loader.getCACertificates();
            assertNotNull(caList, "CA bundle list must not be null");
            assertFalse(caList.isEmpty(), "CA bundle must contain at least one trusted CA certificate");
            log.info("Loaded {} trusted CA certificate(s)", caList.size());

            // 4. Verify Dynamic JSSE Components
            assertNotNull(loader.getKeyManager(), "DynamicKeyManager must be initialized");
            assertNotNull(loader.getTrustManager(), "DynamicTrustManager must be initialized");
            assertNotNull(loader.getDynamicSSLContext(), "DynamicSSLContext must be initialized");
            assertNotNull(loader.getSSLContext(), "SSLContext must be initialized");
            assertNotNull(loader.getHttpClient(), "HttpClient must be initialized");

            // 5. Verify live TLS communication using loader's configured HttpClient
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create(vaultConfig.getBaseUrl() + "/cert/ca/pem"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = loader.getHttpClient().send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "HTTP GET /cert/ca/pem via DynamicSSLContext must return 200 OK");
            assertThat(response.body()).contains("-----BEGIN CERTIFICATE-----");
            log.info("Successfully executed live TLS request to MiniVault via dynamic SSLContext: Status {}", response.statusCode());
        }
    }

    @Test
    @DisplayName("Test direct MiniVault REST client actions with parameters from inventory.xml")
    public void testMiniVaultClientDirectOperations() throws Exception {
        File inventoryXml = findInventoryXml();
        CertManagerConfig config = CertManager.loadConfigFromFile(inventoryXml);
        MiniVaultConfig vaultConfig = config.getVault();

        if (!isMiniVaultReachable(vaultConfig.getBaseUrl())) {
            log.warn("MiniVault server not reachable at '{}'. Skipping direct client test.", vaultConfig.getBaseUrl());
            return;
        }

        // Initialize loader to establish trusted CA SSLContext
        try (CertLoader loader = CertManager.createClientLoader(config)) {
            MiniVaultClient client = ((MiniVaultLoader) loader).getVaultClient();

            // 1. Query Parent Token Metadata
            Token parentToken = client.getToken(vaultConfig.getParentToken());
            assertNotNull(parentToken, "Parent token metadata must not be null");
            assertEquals("parent", parentToken.getType().getValue());
            assertThat(parentToken.getPolicies()).contains("wsbroker.client");
            log.info("Parent token MaxTTL: {}, CertMaxTTL: {}", parentToken.getMaxTtl(), parentToken.getCertMaxTtl());

            // 2. Issue a Child Token
            TokenCreateRequest tokenReq = TokenCreateRequest.builder()
                    .type(TokenType.CLIENT_CERT)
                    .policies(vaultConfig.getTokenPolicies())
                    .ttl(Duration.ofHours(4))
                    .build();

            String childToken = client.createToken(vaultConfig.getParentToken(), tokenReq);
            assertNotNull(childToken, "Created child token must not be null");
            log.info("Created new child token directly: {}", childToken);

            // 3. Issue Certificate directly using the child token
            CertCreateRequest certReq = CertCreateRequest.builder()
                    .type(vaultConfig.getCertType())
                    .uris(vaultConfig.getUris())
                    .ttl(Duration.ofHours(2))
                    .build();

            KeyCertPair newPair = client.createKeyCertPair(childToken, certReq);
            assertNotNull(newPair, "Created KeyCertPair must not be null");
            assertNotNull(newPair.getCertificate());
            assertNotNull(newPair.getPrivateKey());
            log.info("Created direct certificate with Subject: {}", newPair.getSubjectDN());

            // 4. Fetch CA bundle directly
            List<X509Certificate> caList = client.getCA();
            assertNotNull(caList);
            assertFalse(caList.isEmpty());
            log.info("Fetched {} CA certificates directly from MiniVault API", caList.size());
        }
    }
}

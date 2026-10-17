package org.objectspace.certmanager.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.model.*;
import org.objectspace.certmanager.util.PemUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniVaultClientTest {

    private HttpServer server;
    private MiniVaultClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        client = new MiniVaultClient(baseUrl);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testGetToken() {
        server.createContext("/auth/token/get", exchange -> {
            String tokenHeader = exchange.getRequestHeaders().getFirst("X-Vault-Token");
            assertThat(tokenHeader).isEqualTo("test-token-123");

            String response = """
                    {
                        "type": "client_cert",
                        "expiration": "2026-10-17T15:00:00Z",
                        "policies": ["client-policy"],
                        "parent": "pa.123",
                        "metadata": {"user": "tester"},
                        "maxttl": 3600000000000,
                        "certmaxttl": 1800000000000
                    }
                    """;
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        Token token = client.getToken("test-token-123");
        assertThat(token).isNotNull();
        assertThat(token.getType()).isEqualTo(TokenType.CLIENT_CERT);
        assertThat(token.getParent()).isEqualTo("pa.123");
        assertThat(token.getPolicies()).contains("client-policy");
        assertThat(token.getMaxTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(token.getCertMaxTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void testCreateToken() {
        server.createContext("/auth/token/create", exchange -> {
            String tokenHeader = exchange.getRequestHeaders().getFirst("X-Vault-Token");
            assertThat(tokenHeader).isEqualTo("parent-token-xyz");

            String response = "\"cc.new-child-token-789\"";
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        TokenCreateRequest req = TokenCreateRequest.builder()
                .type(TokenType.CLIENT_CERT)
                .policies(List.of("client-policy"))
                .ttl("24h")
                .build();

        String childToken = client.createToken("parent-token-xyz", req);
        assertThat(childToken).isEqualTo("cc.new-child-token-789");
    }

    @Test
    void testCreateCertAndGetCA() throws Exception {
        KeyCertPair keyCert = PemUtils.generateSelfSigned("client.test.local", List.of("client.test.local"), null, null, Duration.ofDays(1));
        String certPem = PemUtils.toPem(keyCert.getEndEntityCertificate());
        String keyPem = PemUtils.toPem(keyCert.privateKey());
        String caPem = certPem;

        server.createContext("/cert/create", exchange -> {
            String tokenHeader = exchange.getRequestHeaders().getFirst("X-Vault-Token");
            assertThat(tokenHeader).isEqualTo("child-token-abc");

            String response = String.format("""
                    {
                        "cert": "%s",
                        "key": "%s",
                        "ca": "%s"
                    }
                    """, escapeJson(certPem), escapeJson(keyPem), escapeJson(caPem));

            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/cert/ca/pem", exchange -> {
            String response = String.format("""
                    {
                        "ca": "%s"
                    }
                    """, escapeJson(caPem));

            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CertCreateRequest req = CertCreateRequest.builder()
                .type("client_cert")
                .dnsNames(List.of("client.test.local"))
                .ttl("1h")
                .build();

        KeyCertPair pair = client.createKeyCertPair("child-token-abc", req);
        assertThat(pair).isNotNull();
        assertThat(pair.getSubjectDN()).contains("CN=client.test.local");

        List<X509Certificate> caCerts = client.getCA();
        assertThat(caCerts).hasSize(1);
        assertThat(caCerts.get(0).getSubjectX500Principal().getName()).contains("CN=client.test.local");
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}

package org.objectspace.certmanager.loader;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.MiniVaultConfig;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.model.TokenType;
import org.objectspace.certmanager.util.PemUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniVaultLoaderTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        KeyCertPair keyCert = PemUtils.generateSelfSigned("mv-client.local", List.of("mv-client.local"), null, null, Duration.ofDays(1));
        String certPem = PemUtils.toPem(keyCert.getEndEntityCertificate());
        String keyPem = PemUtils.toPem(keyCert.privateKey());

        server.createContext("/auth/token/get", exchange -> {
            String response = """
                    {
                        "type": "parent",
                        "expiration": "2026-10-17T15:00:00Z",
                        "policies": ["admin"],
                        "parent": "ro.root",
                        "maxttl": 86400000000000,
                        "certmaxttl": 43200000000000
                    }
                    """;
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/auth/token/create", exchange -> {
            String response = "\"cc.child-token-111\"";
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/cert/create", exchange -> {
            String response = String.format("""
                    {
                        "cert": "%s",
                        "key": "%s",
                        "ca": "%s"
                    }
                    """, escapeJson(certPem), escapeJson(keyPem), escapeJson(certPem));

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
                    """, escapeJson(certPem));

            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testMiniVaultLoaderStartAndCertAcquisition() {
        MiniVaultConfig config = MiniVaultConfig.builder()
                .baseUrl(baseUrl)
                .parentToken("pa.parent-token-abc")
                .tokenType(TokenType.CLIENT_CERT.getValue())
                .tokenPolicies(List.of("client-policy"))
                .certType("client_cert")
                .dnss(List.of("mv-client.local"))
                .build();

        try (MiniVaultLoader loader = new MiniVaultLoader(config)) {
            loader.start();

            assertThat(loader.getActiveToken()).isEqualTo("cc.child-token-111");
            assertThat(loader.getCurrentCertificate()).isNotNull();
            assertThat(loader.getCurrentCertificate().getSubjectDN()).contains("CN=mv-client.local");
            assertThat(loader.getCACertificates()).hasSize(1);
            assertThat(loader.getSSLContext()).isNotNull();
        }
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}

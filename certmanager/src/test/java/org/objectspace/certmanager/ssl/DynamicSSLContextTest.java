package org.objectspace.certmanager.ssl;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.util.PemUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicSSLContextTest {

    private HttpsServer httpsServer;
    private int port;
    private KeyCertPair caCert;
    private KeyCertPair serverCert;
    private final AtomicReference<String> clientCertSeenByServer = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        // Generate test CA
        caCert = PemUtils.generateCA("Test Root CA", Duration.ofDays(10));

        // Generate CA-signed server cert
        serverCert = PemUtils.generateSignedCertificate(
                "localhost",
                List.of("localhost"),
                null,
                List.of("127.0.0.1"),
                Duration.ofDays(1),
                caCert
        );

        DynamicKeyManager serverKeyManager = new DynamicKeyManager(serverCert);
        DynamicTrustManager serverTrustManager = new DynamicTrustManager(false);
        // Server trusts CA
        serverTrustManager.reload(List.of(caCert.getEndEntityCertificate()));

        DynamicSSLContext serverSslContext = new DynamicSSLContext(serverKeyManager, serverTrustManager);

        httpsServer = HttpsServer.create(new InetSocketAddress(0), 0);
        port = httpsServer.getAddress().getPort();

        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext.getSSLContext()) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    SSLContext c = getSSLContext();
                    SSLParameters sslParams = c.getDefaultSSLParameters();
                    sslParams.setNeedClientAuth(true); // require client mTLS
                    params.setSSLParameters(sslParams);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        httpsServer.createContext("/test", exchange -> {
            try {
                if (exchange instanceof HttpsExchange httpsExchange) {
                    SSLSession session = httpsExchange.getSSLSession();
                    Certificate[] clientCerts = session.getPeerCertificates();
                    if (clientCerts != null && clientCerts.length > 0 && clientCerts[0] instanceof X509Certificate x509) {
                        clientCertSeenByServer.set(x509.getSubjectX500Principal().getName());
                    }
                }
            } catch (Exception ignored) {
            }

            byte[] resp = "OK".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        httpsServer.start();
    }

    @AfterEach
    void tearDown() {
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
    }

    @Test
    void testDynamicClientCertReloading() throws Exception {
        // Generate Client Cert #1 signed by CA
        KeyCertPair clientCert1 = PemUtils.generateSignedCertificate("client-1.test.local", List.of("client-1.test.local"), null, null, Duration.ofDays(1), caCert);
        // Generate Client Cert #2 signed by CA
        KeyCertPair clientCert2 = PemUtils.generateSignedCertificate("client-2.test.local", List.of("client-2.test.local"), null, null, Duration.ofDays(1), caCert);

        // Client Dynamic SSLContext
        DynamicKeyManager clientKeyManager = new DynamicKeyManager(clientCert1);
        DynamicTrustManager clientTrustManager = new DynamicTrustManager(false);
        // Client trusts CA
        clientTrustManager.reload(List.of(caCert.getEndEntityCertificate()));

        DynamicSSLContext clientDynamicSSLContext = new DynamicSSLContext(clientKeyManager, clientTrustManager);

        // 1st request with clientCert1
        clientCertSeenByServer.set(null);
        try (javax.net.ssl.SSLSocket socket1 = (javax.net.ssl.SSLSocket) clientDynamicSSLContext.getSocketFactory().createSocket("localhost", port)) {
            socket1.startHandshake();
            OutputStream os = socket1.getOutputStream();
            os.write("GET /test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes());
            os.flush();
            socket1.getInputStream().readAllBytes();
        }
        assertThat(clientCertSeenByServer.get()).contains("CN=client-1.test.local");

        // Dynamically update to clientCert2 on the fly in the KeyManager!
        clientKeyManager.setKeyCertPair(clientCert2);
        clientDynamicSSLContext.invalidateSessions();

        // 2nd request with clientCert2 using the SAME SSLContext
        clientCertSeenByServer.set(null);
        try (javax.net.ssl.SSLSocket socket2 = (javax.net.ssl.SSLSocket) clientDynamicSSLContext.getSocketFactory().createSocket("localhost", port)) {
            socket2.startHandshake();
            OutputStream os = socket2.getOutputStream();
            os.write("GET /test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes());
            os.flush();
            socket2.getInputStream().readAllBytes();
        }
        assertThat(clientCertSeenByServer.get()).contains("CN=client-2.test.local");
    }
}

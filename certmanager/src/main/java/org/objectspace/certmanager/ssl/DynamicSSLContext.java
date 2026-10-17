package org.objectspace.certmanager.ssl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates and wraps an SSLContext pre-configured with DynamicKeyManager and DynamicTrustManager.
 */
public class DynamicSSLContext {

    private static final Logger log = LoggerFactory.getLogger(DynamicSSLContext.class);

    private final DynamicKeyManager keyManager;
    private final DynamicTrustManager trustManager;
    private final SSLContext sslContext;
    private final String protocol;

    public DynamicSSLContext(DynamicKeyManager keyManager, DynamicTrustManager trustManager) {
        this(keyManager, trustManager, "TLS");
    }

    public DynamicSSLContext(DynamicKeyManager keyManager, DynamicTrustManager trustManager, String protocol) {
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager cannot be null");
        this.trustManager = Objects.requireNonNull(trustManager, "trustManager cannot be null");
        this.protocol = protocol != null && !protocol.isBlank() ? protocol : "TLS";

        try {
            this.sslContext = SSLContext.getInstance(this.protocol);
            this.sslContext.init(
                    new KeyManager[]{this.keyManager},
                    new TrustManager[]{this.trustManager},
                    new SecureRandom()
            );
            log.info("Initialized DynamicSSLContext with protocol '{}'", this.protocol);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Failed to initialize DynamicSSLContext: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize DynamicSSLContext", e);
        }
    }

    public DynamicKeyManager getKeyManager() {
        return keyManager;
    }

    public DynamicTrustManager getTrustManager() {
        return trustManager;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public SSLSocketFactory getSocketFactory() {
        return sslContext.getSocketFactory();
    }

    public SSLServerSocketFactory getServerSocketFactory() {
        return sslContext.getServerSocketFactory();
    }

    /**
     * Invalidates all cached SSL/TLS client and server sessions so subsequent connections perform a fresh handshake.
     */
    public void invalidateSessions() {
        try {
            SSLSessionContext clientCtx = sslContext.getClientSessionContext();
            if (clientCtx != null) {
                java.util.Enumeration<byte[]> ids = clientCtx.getIds();
                while (ids.hasMoreElements()) {
                    byte[] id = ids.nextElement();
                    SSLSession session = clientCtx.getSession(id);
                    if (session != null) {
                        session.invalidate();
                    }
                }
            }
            SSLSessionContext serverCtx = sslContext.getServerSessionContext();
            if (serverCtx != null) {
                java.util.Enumeration<byte[]> ids = serverCtx.getIds();
                while (ids.hasMoreElements()) {
                    byte[] id = ids.nextElement();
                    SSLSession session = serverCtx.getSession(id);
                    if (session != null) {
                        session.invalidate();
                    }
                }
            }
            log.info("Invalidated SSL/TLS session caches");
        } catch (Exception e) {
            log.warn("Failed to invalidate SSL session caches: {}", e.getMessage());
        }
    }

    /**
     * Creates a new java.net.http.HttpClient configured with this DynamicSSLContext.
     */
    public HttpClient createHttpClient(Duration timeout) {
        return HttpClient.newBuilder()
                .sslContext(this.sslContext)
                .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(30))
                .build();
    }

    /**
     * Configures an existing HttpClient.Builder with this DynamicSSLContext.
     */
    public HttpClient.Builder configureHttpClient(HttpClient.Builder builder) {
        return builder.sslContext(this.sslContext);
    }
}

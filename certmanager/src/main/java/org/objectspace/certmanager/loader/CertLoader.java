package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.ssl.DynamicKeyManager;
import org.objectspace.certmanager.ssl.DynamicSSLContext;
import org.objectspace.certmanager.ssl.DynamicTrustManager;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Common interface for all certificate loader backends (MiniVault, File, Env, Dev).
 */
public interface CertLoader extends AutoCloseable {

    /**
     * Starts background workers/loops for periodic renewal and monitoring.
     */
    void start();

    /**
     * Gets the currently active X.509 certificate and private key pair.
     */
    KeyCertPair getCurrentCertificate();

    /**
     * Gets the active CA trust certificates.
     */
    List<X509Certificate> getCACertificates();

    /**
     * Gets the dynamic KeyManager used for hot-reloading in SSLContext.
     */
    DynamicKeyManager getKeyManager();

    /**
     * Gets the dynamic TrustManager used for hot-reloading in SSLContext.
     */
    DynamicTrustManager getTrustManager();

    /**
     * Gets the pre-configured DynamicSSLContext wrapper.
     */
    DynamicSSLContext getDynamicSSLContext();

    /**
     * Gets the underlying initialized SSLContext.
     */
    default SSLContext getSSLContext() {
        return getDynamicSSLContext().getSSLContext();
    }

    /**
     * Creates or gets an HttpClient pre-configured with this loader's dynamic SSLContext.
     */
    default HttpClient getHttpClient() {
        return getDynamicSSLContext().createHttpClient(null);
    }

    /**
     * Registers a callback listener invoked whenever the active certificate is renewed or reloaded.
     */
    void addListener(Consumer<KeyCertPair> listener);

    /**
     * Unregisters a certificate update listener.
     */
    void removeListener(Consumer<KeyCertPair> listener);

    /**
     * Stops background loops and releases resources.
     */
    @Override
    void close();
}

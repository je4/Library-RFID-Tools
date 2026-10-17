package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.ssl.DynamicKeyManager;
import org.objectspace.certmanager.ssl.DynamicSSLContext;
import org.objectspace.certmanager.ssl.DynamicTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Base abstract implementation of CertLoader handling SSLContext orchestration and listener notifications.
 */
public abstract class AbstractCertLoader implements CertLoader {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final DynamicKeyManager keyManager;
    protected final DynamicTrustManager trustManager;
    protected final DynamicSSLContext sslContext;

    protected final List<Consumer<KeyCertPair>> listeners = new CopyOnWriteArrayList<>();
    protected final AtomicReference<List<X509Certificate>> caCertificates = new AtomicReference<>(Collections.emptyList());
    protected final AtomicBoolean started = new AtomicBoolean(false);
    protected final AtomicBoolean closed = new AtomicBoolean(false);

    protected ScheduledExecutorService scheduler;

    protected AbstractCertLoader(boolean useSystemRoots, String tlsProtocol) {
        this.keyManager = new DynamicKeyManager();
        this.trustManager = new DynamicTrustManager(useSystemRoots);
        this.sslContext = new DynamicSSLContext(this.keyManager, this.trustManager, tlsProtocol);
    }

    protected void notifyListeners(KeyCertPair newPair) {
        if (newPair == null) {
            return;
        }
        for (Consumer<KeyCertPair> listener : listeners) {
            try {
                listener.accept(newPair);
            } catch (Exception e) {
                log.error("Exception in certificate update listener: {}", e.getMessage(), e);
            }
        }
    }

    protected synchronized void setCertificate(KeyCertPair pair) {
        this.keyManager.setKeyCertPair(pair);
        this.sslContext.invalidateSessions();
        notifyListeners(pair);
    }

    protected synchronized void setCACertificates(List<X509Certificate> caCerts) {
        List<X509Certificate> copy = caCerts != null ? new ArrayList<>(caCerts) : Collections.emptyList();
        this.caCertificates.set(copy);
        this.trustManager.reload(copy);
    }

    @Override
    public KeyCertPair getCurrentCertificate() {
        return keyManager.getKeyCertPair();
    }

    @Override
    public List<X509Certificate> getCACertificates() {
        return Collections.unmodifiableList(caCertificates.get());
    }

    @Override
    public DynamicKeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public DynamicTrustManager getTrustManager() {
        return trustManager;
    }

    @Override
    public DynamicSSLContext getDynamicSSLContext() {
        return sslContext;
    }

    @Override
    public void addListener(Consumer<KeyCertPair> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Consumer<KeyCertPair> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closing {}...", getClass().getSimpleName());
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        }
    }
}

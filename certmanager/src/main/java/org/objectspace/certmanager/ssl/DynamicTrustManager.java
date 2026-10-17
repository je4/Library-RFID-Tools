package org.objectspace.certmanager.ssl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe dynamic TrustManager that can hot-reload CA trust anchors without restarting or re-initializing the SSLContext.
 */
public class DynamicTrustManager extends X509ExtendedTrustManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicTrustManager.class);

    private final AtomicReference<X509ExtendedTrustManager> delegate = new AtomicReference<>();
    private final boolean includeSystemRoots;

    public DynamicTrustManager() {
        this(true);
    }

    public DynamicTrustManager(boolean includeSystemRoots) {
        this.includeSystemRoots = includeSystemRoots;
        reload(Collections.emptyList());
    }

    public DynamicTrustManager(List<X509Certificate> initialCAs, boolean includeSystemRoots) {
        this.includeSystemRoots = includeSystemRoots;
        reload(initialCAs != null ? initialCAs : Collections.emptyList());
    }

    /**
     * Reloads the CA trust anchors.
     */
    public synchronized void reload(List<X509Certificate> caCerts) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            int count = 0;

            if (includeSystemRoots) {
                try {
                    TrustManagerFactory sysTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    sysTmf.init((KeyStore) null);
                    for (TrustManager tm : sysTmf.getTrustManagers()) {
                        if (tm instanceof X509TrustManager x509Tm) {
                            for (X509Certificate cert : x509Tm.getAcceptedIssuers()) {
                                keyStore.setCertificateEntry("sys-root-" + (count++), cert);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not load system root certificates: {}", e.getMessage());
                }
            }

            if (caCerts != null) {
                for (X509Certificate cert : caCerts) {
                    String alias = "ca-" + (count++) + "-" + cert.getSubjectX500Principal().getName();
                    keyStore.setCertificateEntry(alias, cert);
                }
            }

            tmf.init(keyStore);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509ExtendedTrustManager extended) {
                    this.delegate.set(extended);
                    log.info("TrustManager updated with {} trust anchors", count);
                    return;
                } else if (tm instanceof X509TrustManager standard) {
                    this.delegate.set(new DelegatingExtendedTrustManager(standard));
                    log.info("TrustManager updated with {} trust anchors", count);
                    return;
                }
            }

            throw new IllegalStateException("No X509TrustManager found in TrustManagerFactory");
        } catch (Exception e) {
            log.error("Failed to reload TrustManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reload TrustManager", e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            delegate.get().checkClientTrusted(chain, authType, socket);
        } catch (CertificateException e) {
            checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            delegate.get().checkServerTrusted(chain, authType, socket);
        } catch (CertificateException e) {
            checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        try {
            delegate.get().checkClientTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        try {
            delegate.get().checkServerTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.get().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.get().checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        X509ExtendedTrustManager tm = delegate.get();
        return tm != null ? tm.getAcceptedIssuers() : new X509Certificate[0];
    }

    private static class DelegatingExtendedTrustManager extends X509ExtendedTrustManager {
        private final X509TrustManager standard;

        public DelegatingExtendedTrustManager(X509TrustManager standard) {
            this.standard = standard;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            standard.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            standard.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            standard.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            standard.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            standard.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            standard.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return standard.getAcceptedIssuers();
        }
    }
}

package org.objectspace.certmanager.ssl;

import org.objectspace.certmanager.model.KeyCertPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe dynamic KeyManager that can hot-reload X.509 client and server certificates and private keys
 * without restarting or re-initializing the SSLContext.
 */
public class DynamicKeyManager extends X509ExtendedKeyManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicKeyManager.class);
    private static final String DEFAULT_ALIAS = "certmanager-key";

    private final AtomicReference<KeyCertPair> currentPair = new AtomicReference<>();

    public DynamicKeyManager() {
    }

    public DynamicKeyManager(KeyCertPair initialPair) {
        this.currentPair.set(initialPair);
    }

    /**
     * Updates the active certificate and private key. All subsequent SSL/TLS handshakes will immediately use this pair.
     */
    public void setKeyCertPair(KeyCertPair keyCertPair) {
        KeyCertPair old = this.currentPair.getAndSet(keyCertPair);
        if (keyCertPair != null) {
            log.info("KeyManager updated certificate: subject='{}', validUntil={}",
                    keyCertPair.getSubjectDN(), keyCertPair.getNotAfter());
        } else {
            log.warn("KeyManager certificate cleared");
        }
    }

    public KeyCertPair getKeyCertPair() {
        return this.currentPair.get();
    }

    private String getAlias() {
        KeyCertPair pair = currentPair.get();
        if (pair == null) {
            return null;
        }
        return "certmanager-key-" + pair.getEndEntityCertificate().getSerialNumber().toString(16);
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return getAlias();
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return getAlias();
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return getAlias();
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return getAlias();
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        String alias = getAlias();
        return alias != null ? new String[]{alias} : null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        String alias = getAlias();
        return alias != null ? new String[]{alias} : null;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        KeyCertPair pair = currentPair.get();
        if (pair != null) {
            return pair.certificateChain();
        }
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        KeyCertPair pair = currentPair.get();
        if (pair != null) {
            return pair.privateKey();
        }
        return null;
    }
}

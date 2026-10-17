package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.config.EnvConfig;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.util.PemUtils;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Certificate loader backend that reads certificate and key from environment variables or static PEM strings.
 */
public class EnvLoader extends AbstractCertLoader {

    private final EnvConfig config;

    public EnvLoader(EnvConfig config) {
        this(config, "TLS");
    }

    public EnvLoader(EnvConfig config, String tlsProtocol) {
        super(config != null && config.isUseSystemPool(), tlsProtocol);
        this.config = Objects.requireNonNull(config, "EnvConfig cannot be null");

        if (config.getCaContent() != null && !config.getCaContent().isEmpty()) {
            List<X509Certificate> initialCas = new ArrayList<>();
            for (String caPem : config.getCaContent()) {
                try {
                    initialCas.addAll(PemUtils.parseCertificates(caPem));
                } catch (Exception e) {
                    log.warn("Failed to load CA in EnvConfig: {}", e.getMessage());
                }
            }
            if (!initialCas.isEmpty()) {
                setCACertificates(initialCas);
            }
        }
    }

    public EnvConfig getConfig() {
        return config;
    }

    @Override
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        String certPem = config.getCertContent();
        String keyPem = config.getKeyContent();

        if (certPem == null || certPem.isBlank() || keyPem == null || keyPem.isBlank()) {
            throw new IllegalStateException("Environment variables or config content for certificate/key is missing or empty");
        }

        try {
            KeyCertPair pair = PemUtils.parseKeyCertPair(certPem, keyPem);
            setCertificate(pair);
            log.info("EnvLoader initialized certificate: subject='{}', validUntil={}", pair.getSubjectDN(), pair.getNotAfter());
        } catch (Exception e) {
            log.error("Failed to parse certificate/key from environment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize EnvLoader", e);
        }
    }
}

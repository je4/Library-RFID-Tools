package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.config.DevConfig;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.util.PemUtils;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Certificate loader backend that generates in-memory self-signed certificates for testing and development.
 */
public class DevLoader extends AbstractCertLoader {

    private final DevConfig config;

    public DevLoader(DevConfig config) {
        this(config, "TLS");
    }

    public DevLoader(DevConfig config, String tlsProtocol) {
        super(false, tlsProtocol);
        this.config = Objects.requireNonNull(config, "DevConfig cannot be null");
    }

    public DevConfig getConfig() {
        return config;
    }

    @Override
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        log.info("Starting DevLoader generating self-signed certificates for CN='{}'...", config.getCommonName());
        generateAndSetCertificate();

        Duration interval = config.getInterval() != null && !config.getInterval().isZero() ? config.getInterval() : Duration.ofDays(10);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dev-cert-loader-renew");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::generateAndSetCertificate, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void generateAndSetCertificate() {
        try {
            KeyCertPair pair = PemUtils.generateSelfSigned(
                    config.getCommonName(),
                    config.getDnss(),
                    config.getUris(),
                    config.getIps(),
                    config.getValidity()
            );

            // In Dev mode, trust the generated self-signed certificate as CA
            setCACertificates(List.of(pair.getEndEntityCertificate()));
            setCertificate(pair);
            log.info("Generated new self-signed dev certificate: subject='{}', validUntil={}",
                    pair.getSubjectDN(), pair.getNotAfter());
        } catch (Exception e) {
            log.error("Failed to generate self-signed dev certificate: {}", e.getMessage(), e);
        }
    }
}

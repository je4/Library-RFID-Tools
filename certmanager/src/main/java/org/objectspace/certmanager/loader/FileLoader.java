package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.config.FileConfig;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.util.PemUtils;

import java.io.File;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Certificate loader backend that monitors certificate and key files on disk and hot-reloads them when updated.
 */
public class FileLoader extends AbstractCertLoader {

    private final FileConfig config;
    private final File certFile;
    private final File keyFile;
    private final AtomicLong lastModifiedCert = new AtomicLong(0);
    private final AtomicLong lastModifiedKey = new AtomicLong(0);

    public FileLoader(FileConfig config) {
        this(config, "TLS");
    }

    public FileLoader(FileConfig config, String tlsProtocol) {
        super(config != null && config.isUseSystemPool(), tlsProtocol);
        this.config = Objects.requireNonNull(config, "FileConfig cannot be null");
        this.certFile = new File(Objects.requireNonNull(config.getCertFile(), "certFile cannot be null"));
        this.keyFile = new File(Objects.requireNonNull(config.getKeyFile(), "keyFile cannot be null"));

        if (config.getCa() != null && !config.getCa().isEmpty()) {
            List<X509Certificate> initialCas = new ArrayList<>();
            for (String caEntry : config.getCa()) {
                try {
                    File caFile = new File(caEntry);
                    if (caFile.exists() && caFile.isFile()) {
                        String caContent = Files.readString(caFile.toPath());
                        initialCas.addAll(PemUtils.parseCertificates(caContent));
                    } else {
                        initialCas.addAll(PemUtils.parseCertificates(caEntry));
                    }
                } catch (Exception e) {
                    log.warn("Failed to load CA entry in FileConfig: {}", e.getMessage());
                }
            }
            if (!initialCas.isEmpty()) {
                setCACertificates(initialCas);
            }
        }
    }

    public FileConfig getConfig() {
        return config;
    }

    @Override
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        log.info("Starting FileLoader monitoring cert='{}', key='{}'...", certFile.getAbsolutePath(), keyFile.getAbsolutePath());

        // Initial load
        loadFromDisk();

        // Start polling scheduler
        Duration interval = config.getInterval() != null && !config.getInterval().isZero() ? config.getInterval() : Duration.ofMinutes(1);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "file-cert-loader-poller");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::checkAndReload, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("FileLoader started with polling interval {}", interval);
    }

    private void loadFromDisk() {
        try {
            if (!certFile.exists() || !keyFile.exists()) {
                throw new IllegalStateException("Certificate or key file does not exist: cert=" + certFile.exists() + ", key=" + keyFile.exists());
            }

            String certPem = Files.readString(certFile.toPath());
            String keyPem = Files.readString(keyFile.toPath());

            KeyCertPair pair = PemUtils.parseKeyCertPair(certPem, keyPem);
            lastModifiedCert.set(certFile.lastModified());
            lastModifiedKey.set(keyFile.lastModified());

            setCertificate(pair);
            log.info("Loaded certificate from disk: subject='{}', validUntil={}", pair.getSubjectDN(), pair.getNotAfter());
        } catch (Exception e) {
            log.error("Failed to load certificate/key from disk: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load certificate from disk", e);
        }
    }

    private void checkAndReload() {
        if (closed.get()) {
            return;
        }
        try {
            long currentCertMod = certFile.lastModified();
            long currentKeyMod = keyFile.lastModified();

            if (currentCertMod > lastModifiedCert.get() || currentKeyMod > lastModifiedKey.get()) {
                log.info("Detected change in certificate or key file. Reloading...");
                loadFromDisk();
            }
        } catch (Exception e) {
            log.error("Error checking/reloading files: {}", e.getMessage());
        }
    }
}

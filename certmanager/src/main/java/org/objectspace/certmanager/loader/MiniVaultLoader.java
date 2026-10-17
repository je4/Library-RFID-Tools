package org.objectspace.certmanager.loader;

import org.objectspace.certmanager.client.MiniVaultClient;
import org.objectspace.certmanager.config.MiniVaultConfig;
import org.objectspace.certmanager.model.*;
import org.objectspace.certmanager.util.DurationParser;
import org.objectspace.certmanager.util.PemUtils;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Certificate loader backend for MiniVault PKI service.
 * Automatically renews child tokens and reissues short-lived client/server certificates.
 */
public class MiniVaultLoader extends AbstractCertLoader {

    private final MiniVaultConfig config;
    private final MiniVaultClient vaultClient;
    private final AtomicReference<String> activeToken = new AtomicReference<>();

    private Duration tokenTtl;
    private Duration certTtl;
    private Duration tokenInterval;
    private Duration certInterval;

    public MiniVaultLoader(MiniVaultConfig config) {
        this(config, "TLS");
    }

    public MiniVaultLoader(MiniVaultConfig config, String tlsProtocol) {
        super(config != null && config.isUseSystemPool(), tlsProtocol);
        this.config = Objects.requireNonNull(config, "MiniVaultConfig cannot be null");

        this.tokenTtl = config.getTokenTtl();
        this.certTtl = config.getCertTtl();
        this.tokenInterval = config.getTokenInterval();
        this.certInterval = config.getCertInterval();

        // Load custom CA if provided in config
        if (config.getCa() != null && !config.getCa().isEmpty()) {
            List<X509Certificate> initialCas = new ArrayList<>();
            for (String caPem : config.getCa()) {
                try {
                    initialCas.addAll(PemUtils.parseCertificates(caPem));
                } catch (Exception e) {
                    log.warn("Failed to parse custom CA configured in MiniVaultConfig: {}", e.getMessage());
                }
            }
            if (!initialCas.isEmpty()) {
                setCACertificates(initialCas);
            }
        }

        this.vaultClient = new MiniVaultClient(config.getBaseUrl(), this.sslContext.getSSLContext(), Duration.ofSeconds(30));
    }

    public MiniVaultClient getVaultClient() {
        return vaultClient;
    }

    public MiniVaultConfig getConfig() {
        return config;
    }

    public String getActiveToken() {
        return activeToken.get();
    }

    @Override
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        log.info("Starting MiniVaultLoader for endpoint '{}'...", config.getBaseUrl());

        // Discover TTLs from parent token if needed
        discoverTtls();

        // Perform initial token fetch
        String token = loadTokenWithRetry();
        activeToken.set(token);

        // Perform initial certificate fetch
        KeyCertPair initialPair = loadCertWithRetry();
        setCertificate(initialPair);

        // Perform initial CA fetch from vault
        try {
            List<X509Certificate> caCerts = vaultClient.getCA();
            if (caCerts != null && !caCerts.isEmpty()) {
                setCACertificates(caCerts);
            }
        } catch (Exception e) {
            log.warn("Could not fetch initial CA bundle from MiniVault: {}", e.getMessage());
        }

        // Start background renewal schedulers
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "minivault-loader-worker");
            t.setDaemon(true);
            return t;
        });

        long tokenIntervalMs = tokenInterval != null ? tokenInterval.toMillis() : Duration.ofHours(12).toMillis();
        long certIntervalMs = certInterval != null ? certInterval.toMillis() : Duration.ofHours(6).toMillis();

        scheduler.scheduleWithFixedDelay(this::tokenRenewalTask, tokenIntervalMs, tokenIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::certRenewalTask, certIntervalMs, certIntervalMs, TimeUnit.MILLISECONDS);

        log.info("MiniVaultLoader started successfully (token renewal: {}, cert renewal: {})",
                DurationParser.format(Duration.ofMillis(tokenIntervalMs)),
                DurationParser.format(Duration.ofMillis(certIntervalMs)));
    }

    private void discoverTtls() {
        if (tokenTtl == null || tokenTtl.isZero()) {
            try {
                Token parentMeta = vaultClient.getToken(config.getParentToken());
                if (parentMeta != null) {
                    tokenTtl = parentMeta.getMaxTtl();
                    if (certTtl == null || certTtl.isZero()) {
                        certTtl = parentMeta.getCertMaxTtl();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to query parent token TTL: {}. Using default TTLs.", e.getMessage());
            }
        }

        if (tokenTtl == null || tokenTtl.isZero()) {
            tokenTtl = Duration.ofDays(1);
        }
        if (certTtl == null || certTtl.isZero()) {
            certTtl = tokenTtl.dividedBy(10);
        }
        if (tokenInterval == null || tokenInterval.isZero() || tokenInterval.compareTo(tokenTtl.minus(certTtl)) > 0) {
            Duration twoThirds = tokenTtl.multipliedBy(2).dividedBy(3);
            Duration diff = tokenTtl.minus(certTtl);
            tokenInterval = twoThirds.compareTo(diff) < 0 ? twoThirds : diff;
            if (tokenInterval.isNegative() || tokenInterval.isZero()) {
                tokenInterval = tokenTtl.dividedBy(2);
            }
        }
        if (certInterval == null || certInterval.isZero() || certInterval.compareTo(certTtl.multipliedBy(2).dividedBy(3)) > 0) {
            certInterval = certTtl.multipliedBy(2).dividedBy(3);
        }

        log.info("Configured TTLs: tokenTTL={}, tokenInterval={}, certTTL={}, certInterval={}",
                DurationParser.format(tokenTtl), DurationParser.format(tokenInterval),
                DurationParser.format(certTtl), DurationParser.format(certInterval));
    }

    private String loadTokenWithRetry() {
        int attempts = 0;
        while (!closed.get()) {
            attempts++;
            try {
                TokenCreateRequest req = TokenCreateRequest.builder()
                        .type(TokenType.fromValue(config.getTokenType()))
                        .policies(config.getTokenPolicies())
                        .ttl(tokenTtl != null ? DurationParser.format(tokenTtl) : "24h")
                        .build();

                String token = vaultClient.createToken(config.getParentToken(), req);
                if (token != null && !token.isBlank()) {
                    log.info("Obtained child token from MiniVault successfully (attempt {})", attempts);
                    return token;
                }
            } catch (Exception e) {
                log.error("Failed to load child token from MiniVault (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to retry token load", e);
            }
        }
        throw new IllegalStateException("MiniVaultLoader closed while waiting for token");
    }

    private KeyCertPair loadCertWithRetry() {
        int attempts = 0;
        while (!closed.get()) {
            attempts++;
            try {
                String token = activeToken.get();
                if (token != null) {
                    CertCreateRequest req = CertCreateRequest.builder()
                            .type(config.getCertType())
                            .uris(config.getUris())
                            .dnsNames(config.getDnss())
                            .ips(config.getIps())
                            .ttl(certTtl != null ? DurationParser.format(certTtl) : "24h")
                            .build();

                    KeyCertPair pair = vaultClient.createKeyCertPair(token, req);
                    log.info("Obtained certificate from MiniVault successfully (attempt {}): subject='{}', validUntil={}",
                            attempts, pair.getSubjectDN(), pair.getNotAfter());
                    return pair;
                }
            } catch (Exception e) {
                log.error("Failed to load certificate from MiniVault (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to retry cert load", e);
            }
        }
        throw new IllegalStateException("MiniVaultLoader closed while waiting for certificate");
    }

    private void tokenRenewalTask() {
        if (closed.get()) {
            return;
        }
        log.debug("Executing scheduled token renewal...");
        try {
            TokenCreateRequest req = TokenCreateRequest.builder()
                    .type(TokenType.fromValue(config.getTokenType()))
                    .policies(config.getTokenPolicies())
                    .ttl(tokenTtl != null ? DurationParser.format(tokenTtl) : "24h")
                    .build();

            String token = vaultClient.createToken(config.getParentToken(), req);
            activeToken.set(token);
            log.info("Child token renewed successfully");
        } catch (Exception e) {
            log.error("Failed to renew child token in background: {}", e.getMessage());
        }
    }

    private void certRenewalTask() {
        if (closed.get()) {
            return;
        }
        log.debug("Executing scheduled certificate renewal...");
        try {
            String token = activeToken.get();
            if (token == null) {
                log.warn("Cannot renew certificate: no active token");
                return;
            }
            CertCreateRequest req = CertCreateRequest.builder()
                    .type(config.getCertType())
                    .uris(config.getUris())
                    .dnsNames(config.getDnss())
                    .ips(config.getIps())
                    .ttl(certTtl != null ? DurationParser.format(certTtl) : "24h")
                    .build();

            KeyCertPair pair = vaultClient.createKeyCertPair(token, req);
            setCertificate(pair);
            log.info("Certificate renewed and hot-reloaded successfully: subject='{}'", pair.getSubjectDN());

            // Also refresh CA bundle
            try {
                List<X509Certificate> caCerts = vaultClient.getCA();
                if (caCerts != null && !caCerts.isEmpty()) {
                    setCACertificates(caCerts);
                }
            } catch (Exception e) {
                log.warn("Failed to refresh CA certificates: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to renew certificate in background: {}", e.getMessage());
        }
    }
}

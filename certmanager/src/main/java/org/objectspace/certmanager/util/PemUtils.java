package org.objectspace.certmanager.util;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.objectspace.certmanager.model.KeyCertPair;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Utility for parsing, converting, and generating PEM certificates and keys.
 */
public final class PemUtils {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private PemUtils() {
    }

    private static String resolvePemContent(String pemContentOrPath) {
        if (pemContentOrPath == null || pemContentOrPath.isBlank()) {
            return pemContentOrPath;
        }
        if (pemContentOrPath.contains("-----BEGIN")) {
            return pemContentOrPath;
        }
        File f = new File(pemContentOrPath);
        if (f.exists() && f.isFile()) {
            try {
                return java.nio.file.Files.readString(f.toPath());
            } catch (IOException ignored) {
            }
        }
        File parentRel = new File("..", pemContentOrPath);
        if (parentRel.exists() && parentRel.isFile()) {
            try {
                return java.nio.file.Files.readString(parentRel.toPath());
            } catch (IOException ignored) {
            }
        }
        return pemContentOrPath;
    }

    /**
     * Parses all X.509 certificates from a PEM string or file path.
     */
    public static List<X509Certificate> parseCertificates(String pemContentOrPath) throws IOException, GeneralSecurityException {
        if (pemContentOrPath == null || pemContentOrPath.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String pemContent = resolvePemContent(pemContentOrPath);
        List<X509Certificate> certs = new ArrayList<>();
        try (PEMParser pemParser = new PEMParser(new StringReader(pemContent))) {
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            Object obj;
            while ((obj = pemParser.readObject()) != null) {
                if (obj instanceof X509CertificateHolder holder) {
                    certs.add(converter.getCertificate(holder));
                } else if (obj instanceof X509Certificate cert) {
                    certs.add(cert);
                }
            }
        }

        // Fallback to standard CertificateFactory if BouncyCastle PEMParser did not return certificates
        if (certs.isEmpty() && pemContent.contains("-----BEGIN")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> parsed = cf.generateCertificates(new ByteArrayInputStream(pemContent.getBytes()));
            for (Certificate c : parsed) {
                if (c instanceof X509Certificate x509) {
                    certs.add(x509);
                }
            }
        }

        return certs;
    }

    /**
     * Parses a PrivateKey (PKCS#1, PKCS#8, EC or RSA) from a PEM string or file path.
     */
    public static PrivateKey parsePrivateKey(String pemContentOrPath) throws IOException, GeneralSecurityException {
        if (pemContentOrPath == null || pemContentOrPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key PEM content is empty");
        }
        String pemContent = resolvePemContent(pemContentOrPath);
        try (PEMParser pemParser = new PEMParser(new StringReader(pemContent))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            Object obj = pemParser.readObject();
            if (obj == null) {
                throw new GeneralSecurityException("No private key object found in PEM content");
            }
            PrivateKey key;
            if (obj instanceof PEMKeyPair keyPair) {
                key = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (obj instanceof PrivateKeyInfo keyInfo) {
                key = converter.getPrivateKey(keyInfo);
            } else if (obj instanceof PrivateKey pk) {
                key = pk;
            } else {
                throw new GeneralSecurityException("Unsupported private key object type: " + obj.getClass().getName());
            }
            return normalizePrivateKey(key);
        }
    }

    /**
     * Normalizes a PrivateKey to use standard Java JCA algorithm names (e.g. "EC" instead of BouncyCastle's "ECDSA")
     * so that SunJSSE and standard SSL/TLS engines recognize the key for client certificate authentication.
     */
    public static PrivateKey normalizePrivateKey(PrivateKey key) {
        if (key == null) {
            return null;
        }
        String algorithm = key.getAlgorithm();
        if ("ECDSA".equalsIgnoreCase(algorithm)) {
            try {
                KeyFactory kf = KeyFactory.getInstance("EC");
                return kf.generatePrivate(new PKCS8EncodedKeySpec(key.getEncoded()));
            } catch (Exception e) {
                if (key instanceof ECPrivateKey ecKey) {
                    return new DelegatingECPrivateKey(ecKey, "EC");
                }
                return new DelegatingPrivateKey(key, "EC");
            }
        }
        return key;
    }

    private static class DelegatingPrivateKey implements PrivateKey {
        protected final PrivateKey delegate;
        protected final String algorithm;

        public DelegatingPrivateKey(PrivateKey delegate, String algorithm) {
            this.delegate = delegate;
            this.algorithm = algorithm;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public byte[] getEncoded() {
            return delegate.getEncoded();
        }

        @Override
        public void destroy() throws javax.security.auth.DestroyFailedException {
            delegate.destroy();
        }

        @Override
        public boolean isDestroyed() {
            return delegate.isDestroyed();
        }
    }

    private static final class DelegatingECPrivateKey extends DelegatingPrivateKey implements ECPrivateKey {
        private final ECPrivateKey ecDelegate;

        public DelegatingECPrivateKey(ECPrivateKey ecDelegate, String algorithm) {
            super(ecDelegate, algorithm);
            this.ecDelegate = ecDelegate;
        }

        @Override
        public BigInteger getS() {
            return ecDelegate.getS();
        }

        @Override
        public java.security.spec.ECParameterSpec getParams() {
            return ecDelegate.getParams();
        }
    }

    /**
     * Creates a KeyCertPair from certificate and private key PEM strings.
     */
    public static KeyCertPair parseKeyCertPair(String certPem, String keyPem) throws IOException, GeneralSecurityException {
        List<X509Certificate> certs = parseCertificates(certPem);
        if (certs.isEmpty()) {
            throw new GeneralSecurityException("No X.509 certificates found in certificate PEM");
        }
        PrivateKey privateKey = parsePrivateKey(keyPem);
        return new KeyCertPair(certs.toArray(new X509Certificate[0]), privateKey);
    }

    /**
     * Exports a certificate or key to PEM string.
     */
    public static String toPem(Object obj) throws IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
        }
        return sw.toString();
    }

    /**
     * Creates an in-memory PKCS12 KeyStore populated with the given CA certificates.
     */
    public static KeyStore createTrustStore(List<X509Certificate> caCerts) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        int idx = 1;
        for (X509Certificate ca : caCerts) {
            String alias = "ca-" + idx++ + "-" + ca.getSubjectX500Principal().getName();
            keyStore.setCertificateEntry(alias, ca);
        }
        return keyStore;
    }

    /**
     * Generates a self-signed Root CA certificate and private key.
     */
    public static KeyCertPair generateCA(String commonName, Duration validity) throws GeneralSecurityException, IOException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name subject = new X500Name("CN=" + (commonName != null && !commonName.isBlank() ? commonName : "Test Root CA"));
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis()).multiply(BigInteger.valueOf(1000)).add(BigInteger.valueOf(new SecureRandom().nextInt(1000)));

        Instant now = Instant.now().minus(Duration.ofMinutes(5));
        Instant expiry = Instant.now().plus(validity != null && !validity.isZero() ? validity : Duration.ofDays(365));

        Date notBefore = Date.from(now);
        Date notAfter = Date.from(expiry);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true)); // is CA

        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(keyPair.getPrivate());
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to initialize certificate signer", e);
        }

        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);

        return new KeyCertPair(new X509Certificate[]{cert}, keyPair.getPrivate());
    }

    /**
     * Generates an X.509 certificate signed by a CA.
     */
    public static KeyCertPair generateSignedCertificate(String commonName,
                                                        List<String> dnsNames,
                                                        List<String> uris,
                                                        List<String> ips,
                                                        Duration validity,
                                                        KeyCertPair caPair) throws GeneralSecurityException, IOException {
        Objects.requireNonNull(caPair, "caPair cannot be null");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name issuer = new X500Name(caPair.getSubjectDN());
        X500Name subject = new X500Name("CN=" + (commonName != null && !commonName.isBlank() ? commonName : "localhost"));
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis()).multiply(BigInteger.valueOf(1000)).add(BigInteger.valueOf(new SecureRandom().nextInt(1000)));

        Instant now = Instant.now().minus(Duration.ofMinutes(5));
        Instant expiry = Instant.now().plus(validity != null && !validity.isZero() ? validity : Duration.ofDays(30));

        Date notBefore = Date.from(now);
        Date notAfter = Date.from(expiry);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        List<GeneralName> altNames = new ArrayList<>();
        if (dnsNames != null) {
            for (String dns : dnsNames) {
                altNames.add(new GeneralName(GeneralName.dNSName, dns));
            }
        }
        if (uris != null) {
            for (String uri : uris) {
                altNames.add(new GeneralName(GeneralName.uniformResourceIdentifier, uri));
            }
        }
        if (ips != null) {
            for (String ip : ips) {
                altNames.add(new GeneralName(GeneralName.iPAddress, ip));
            }
        }
        if (!altNames.isEmpty()) {
            GeneralNames subjectAltNames = new GeneralNames(altNames.toArray(new GeneralName[0]));
            certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        }

        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caPair.privateKey());
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to initialize certificate signer", e);
        }

        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);

        X509Certificate[] chain = new X509Certificate[]{cert, caPair.getEndEntityCertificate()};
        return new KeyCertPair(chain, keyPair.getPrivate());
    }

    /**
     * Generates a self-signed X.509 certificate and RSA 2048 key pair for development and testing.
     */
    public static KeyCertPair generateSelfSigned(String commonName,
                                                 List<String> dnsNames,
                                                 List<String> uris,
                                                 List<String> ips,
                                                 Duration validity) throws GeneralSecurityException, IOException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name subject = new X500Name("CN=" + (commonName != null && !commonName.isBlank() ? commonName : "localhost"));
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis()).multiply(BigInteger.valueOf(1000)).add(BigInteger.valueOf(new SecureRandom().nextInt(1000)));

        Instant now = Instant.now().minus(Duration.ofMinutes(5));
        Instant expiry = Instant.now().plus(validity != null && !validity.isZero() ? validity : Duration.ofDays(30));

        Date notBefore = Date.from(now);
        Date notAfter = Date.from(expiry);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        List<GeneralName> altNames = new ArrayList<>();
        if (dnsNames != null) {
            for (String dns : dnsNames) {
                altNames.add(new GeneralName(GeneralName.dNSName, dns));
            }
        }
        if (uris != null) {
            for (String uri : uris) {
                altNames.add(new GeneralName(GeneralName.uniformResourceIdentifier, uri));
            }
        }
        if (ips != null) {
            for (String ip : ips) {
                altNames.add(new GeneralName(GeneralName.iPAddress, ip));
            }
        }
        if (!altNames.isEmpty()) {
            GeneralNames subjectAltNames = new GeneralNames(altNames.toArray(new GeneralName[0]));
            certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        }

        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(keyPair.getPrivate());
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to initialize certificate signer", e);
        }

        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);

        return new KeyCertPair(new X509Certificate[]{cert}, keyPair.getPrivate());
    }
}

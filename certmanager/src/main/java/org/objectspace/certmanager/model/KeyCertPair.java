package org.objectspace.certmanager.model;

import org.objectspace.certmanager.util.PemUtils;

import java.security.PrivateKey;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable holder for an X.509 certificate chain and matching private key.
 */
public record KeyCertPair(
        X509Certificate[] certificateChain,
        PrivateKey privateKey
) {

    public KeyCertPair {
        Objects.requireNonNull(certificateChain, "certificateChain cannot be null");
        Objects.requireNonNull(privateKey, "privateKey cannot be null");
        if (certificateChain.length == 0) {
            throw new IllegalArgumentException("certificateChain cannot be empty");
        }
        certificateChain = certificateChain.clone();
        privateKey = PemUtils.normalizePrivateKey(privateKey);
    }

    @Override
    public X509Certificate[] certificateChain() {
        return certificateChain.clone();
    }

    public X509Certificate getEndEntityCertificate() {
        return certificateChain[0];
    }

    public X509Certificate getCertificate() {
        return getEndEntityCertificate();
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public Instant getNotBefore() {
        return getEndEntityCertificate().getNotBefore().toInstant();
    }

    public Instant getNotAfter() {
        return getEndEntityCertificate().getNotAfter().toInstant();
    }

    public String getSubjectDN() {
        return getEndEntityCertificate().getSubjectX500Principal().getName();
    }

    public String getIssuerDN() {
        return getEndEntityCertificate().getIssuerX500Principal().getName();
    }

    /**
     * Extracts Subject Alternative Names (SANs) from the end-entity certificate.
     */
    public List<String> getSubjectAlternativeNames() {
        List<String> sans = new ArrayList<>();
        try {
            Collection<List<?>> entries = getEndEntityCertificate().getSubjectAlternativeNames();
            if (entries != null) {
                for (List<?> entry : entries) {
                    if (entry.size() >= 2 && entry.get(1) != null) {
                        sans.add(entry.get(1).toString());
                    }
                }
            }
        } catch (CertificateParsingException ignored) {
        }
        return sans;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(getNotAfter());
    }

    public boolean isNotYetValid() {
        return Instant.now().isBefore(getNotBefore());
    }

    @Override
    public String toString() {
        return "KeyCertPair{" +
                "subject='" + getSubjectDN() + '\'' +
                ", issuer='" + getIssuerDN() + '\'' +
                ", notBefore=" + getNotBefore() +
                ", notAfter=" + getNotAfter() +
                ", chainLength=" + certificateChain.length +
                ", sans=" + getSubjectAlternativeNames() +
                '}';
    }
}

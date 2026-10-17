package org.objectspace.certmanager.util;

import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.model.KeyCertPair;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PemUtilsTest {

    @Test
    void testSelfSignedAndPemRoundTrip() throws Exception {
        KeyCertPair generated = PemUtils.generateSelfSigned(
                "test.example.com",
                List.of("test.example.com", "alt.example.com"),
                List.of("spiffe://example.com/test"),
                List.of("127.0.0.1"),
                Duration.ofDays(10)
        );

        assertThat(generated).isNotNull();
        assertThat(generated.getSubjectDN()).contains("CN=test.example.com");
        assertThat(generated.getSubjectAlternativeNames()).contains("test.example.com", "alt.example.com", "spiffe://example.com/test", "127.0.0.1");
        assertThat(generated.isExpired()).isFalse();

        // Convert to PEM
        String certPem = PemUtils.toPem(generated.getEndEntityCertificate());
        String keyPem = PemUtils.toPem(generated.privateKey());

        assertThat(certPem).contains("-----BEGIN CERTIFICATE-----");
        assertThat(keyPem).contains("-----BEGIN RSA PRIVATE KEY-----");

        // Parse PEM back
        List<X509Certificate> parsedCerts = PemUtils.parseCertificates(certPem);
        assertThat(parsedCerts).hasSize(1);
        assertThat(parsedCerts.get(0).getSubjectX500Principal().getName()).isEqualTo(generated.getSubjectDN());

        PrivateKey parsedKey = PemUtils.parsePrivateKey(keyPem);
        assertThat(parsedKey).isNotNull();

        KeyCertPair parsedPair = PemUtils.parseKeyCertPair(certPem, keyPem);
        assertThat(parsedPair.getSubjectDN()).isEqualTo(generated.getSubjectDN());

        // Test truststore creation
        KeyStore trustStore = PemUtils.createTrustStore(parsedCerts);
        assertThat(trustStore.size()).isEqualTo(1);
    }
}

package org.objectspace.certmanager;

import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.certmanager.loader.CertLoader;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CertManagerTest {

    @Test
    void testLoadConfigFromJson() throws Exception {
        String json = """
                {
                    "type": "MINIVAULT",
                    "initialtimeout": "30s",
                    "tlsminversion": "TLSv1.3",
                    "minivault": {
                        "baseurl": "https://vault.example.com:8200",
                        "parenttoken": "s.parent-secret-123",
                        "tokentype": "client_cert",
                        "tokenpolicies": ["client-policy"],
                        "certtype": "client_cert",
                        "dnss": ["api.internal.local"],
                        "uris": ["spiffe://internal.local/api"],
                        "certttl": "24h",
                        "certinterval": "12h",
                        "usesystempool": true
                    }
                }
                """;

        CertManagerConfig config = CertManager.loadConfigFromJson(json);
        assertThat(config).isNotNull();
        assertThat(config.getType()).isEqualTo(LoaderType.MINIVAULT);
        assertThat(config.getInitialTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getTlsMinVersion()).isEqualTo("TLSv1.3");

        assertThat(config.getVault()).isNotNull();
        assertThat(config.getVault().getBaseUrl()).isEqualTo("https://vault.example.com:8200");
        assertThat(config.getVault().getParentToken()).isEqualTo("s.parent-secret-123");
        assertThat(config.getVault().getDnss()).contains("api.internal.local");
        assertThat(config.getVault().getUris()).contains("spiffe://internal.local/api");
        assertThat(config.getVault().getCertTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(config.getVault().getCertInterval()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void testCreateDevClientLoaderFromConfig() {
        CertManagerConfig config = CertManagerConfig.builder()
                .type(LoaderType.DEV)
                .build();

        try (CertLoader loader = CertManager.createClientLoader(config)) {
            assertThat(loader).isNotNull();
            assertThat(loader.getCurrentCertificate()).isNotNull();
            assertThat(loader.getSSLContext()).isNotNull();
            assertThat(loader.getHttpClient()).isNotNull();
        }
    }
}

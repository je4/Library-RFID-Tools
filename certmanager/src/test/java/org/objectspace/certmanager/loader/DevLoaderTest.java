package org.objectspace.certmanager.loader;

import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.DevConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DevLoaderTest {

    @Test
    void testDevLoaderLifecycle() {
        DevConfig config = DevConfig.builder()
                .commonName("dev-service.local")
                .validity(Duration.ofDays(7))
                .interval(Duration.ofDays(1))
                .build();

        try (DevLoader loader = new DevLoader(config)) {
            loader.start();
            assertThat(loader.getCurrentCertificate()).isNotNull();
            assertThat(loader.getCurrentCertificate().getSubjectDN()).contains("CN=dev-service.local");
            assertThat(loader.getCACertificates()).hasSize(1);
            assertThat(loader.getSSLContext()).isNotNull();
        }
    }
}

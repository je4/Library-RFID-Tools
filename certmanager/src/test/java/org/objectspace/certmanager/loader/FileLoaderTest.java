package org.objectspace.certmanager.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectspace.certmanager.config.FileConfig;
import org.objectspace.certmanager.model.KeyCertPair;
import org.objectspace.certmanager.util.PemUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileLoaderInitialAndHotReload() throws Exception {
        File certFile = tempDir.resolve("tls.crt").toFile();
        File keyFile = tempDir.resolve("tls.key").toFile();

        KeyCertPair cert1 = PemUtils.generateSelfSigned("initial.local", List.of("initial.local"), null, null, Duration.ofDays(1));
        Files.writeString(certFile.toPath(), PemUtils.toPem(cert1.getEndEntityCertificate()));
        Files.writeString(keyFile.toPath(), PemUtils.toPem(cert1.privateKey()));

        FileConfig config = FileConfig.builder()
                .certFile(certFile.getAbsolutePath())
                .keyFile(keyFile.getAbsolutePath())
                .interval(Duration.ofMillis(100))
                .build();

        try (FileLoader loader = new FileLoader(config)) {
            loader.start();

            assertThat(loader.getCurrentCertificate()).isNotNull();
            assertThat(loader.getCurrentCertificate().getSubjectDN()).contains("CN=initial.local");

            CountDownLatch reloadLatch = new CountDownLatch(1);
            AtomicReference<KeyCertPair> reloadedCert = new AtomicReference<>();

            loader.addListener(pair -> {
                reloadedCert.set(pair);
                reloadLatch.countDown();
            });

            // Sleep slightly to guarantee modification time change
            Thread.sleep(100);

            KeyCertPair cert2 = PemUtils.generateSelfSigned("updated.local", List.of("updated.local"), null, null, Duration.ofDays(1));
            Files.writeString(certFile.toPath(), PemUtils.toPem(cert2.getEndEntityCertificate()));
            Files.writeString(keyFile.toPath(), PemUtils.toPem(cert2.privateKey()));
            certFile.setLastModified(System.currentTimeMillis() + 2000);

            boolean reloaded = reloadLatch.await(5, TimeUnit.SECONDS);
            assertThat(reloaded).isTrue();
            assertThat(loader.getCurrentCertificate().getSubjectDN()).contains("CN=updated.local");
        }
    }
}

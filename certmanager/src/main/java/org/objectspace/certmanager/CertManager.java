package org.objectspace.certmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.objectspace.certmanager.config.*;
import org.objectspace.certmanager.loader.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Main factory and entry point for certificate loaders and dynamic TLS/mTLS management.
 */
public final class CertManager {

    private static final Logger log = LoggerFactory.getLogger(CertManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private CertManager() {
    }

    /**
     * Initializes and starts a client-side certificate loader from a top-level configuration.
     *
     * @param config the CertManagerConfig
     * @return started CertLoader instance
     */
    public static CertLoader createClientLoader(CertManagerConfig config) {
        Objects.requireNonNull(config, "CertManagerConfig cannot be null");
        LoaderType type = config.getType() != null ? config.getType() : LoaderType.MINIVAULT;
        String protocol = config.getTlsMinVersion() != null ? config.getTlsMinVersion() : "TLS";

        CertLoader loader = switch (type) {
            case MINIVAULT -> {
                MiniVaultConfig vCfg = config.getVault();
                if (vCfg == null) {
                    throw new IllegalArgumentException("MiniVaultConfig missing in CertManagerConfig");
                }
                yield new MiniVaultLoader(vCfg, protocol);
            }
            case FILE -> {
                FileConfig fCfg = config.getFile();
                if (fCfg == null) {
                    throw new IllegalArgumentException("FileConfig missing in CertManagerConfig");
                }
                yield new FileLoader(fCfg, protocol);
            }
            case ENV -> {
                EnvConfig eCfg = config.getEnv();
                if (eCfg == null) {
                    throw new IllegalArgumentException("EnvConfig missing in CertManagerConfig");
                }
                yield new EnvLoader(eCfg, protocol);
            }
            case DEV -> {
                DevConfig dCfg = config.getDev() != null ? config.getDev() : new DevConfig();
                yield new DevLoader(dCfg, protocol);
            }
        };

        loader.start();
        return loader;
    }

    /**
     * Initializes and starts a server-side certificate loader from a top-level configuration.
     */
    public static CertLoader createServerLoader(CertManagerConfig config) {
        return createClientLoader(config);
    }

    /**
     * Convenience method to create and start a MiniVault certificate loader.
     */
    public static MiniVaultLoader createMiniVaultLoader(MiniVaultConfig config) {
        MiniVaultLoader loader = new MiniVaultLoader(config);
        loader.start();
        return loader;
    }

    /**
     * Convenience method to create and start a filesystem certificate loader.
     */
    public static FileLoader createFileLoader(FileConfig config) {
        FileLoader loader = new FileLoader(config);
        loader.start();
        return loader;
    }

    /**
     * Convenience method to create and start an environment variable certificate loader.
     */
    public static EnvLoader createEnvLoader(EnvConfig config) {
        EnvLoader loader = new EnvLoader(config);
        loader.start();
        return loader;
    }

    /**
     * Convenience method to create and start an in-memory self-signed development certificate loader.
     */
    public static DevLoader createDevLoader(DevConfig config) {
        DevLoader loader = new DevLoader(config != null ? config : new DevConfig());
        loader.start();
        return loader;
    }

    /**
     * Parses a CertManagerConfig from JSON string.
     */
    public static CertManagerConfig loadConfigFromJson(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, CertManagerConfig.class);
    }

    /**
     * Parses a CertManagerConfig from XML string.
     */
    public static CertManagerConfig loadConfigFromXml(String xml) throws Exception {
        return XmlConfigParser.parse(xml);
    }

    /**
     * Parses a CertManagerConfig from an XML file.
     */
    public static CertManagerConfig loadConfigFromXml(File file) throws Exception {
        return XmlConfigParser.parse(file);
    }

    /**
     * Parses a CertManagerConfig from an XML InputStream.
     */
    public static CertManagerConfig loadConfigFromXml(InputStream is) throws Exception {
        return XmlConfigParser.parse(is);
    }

    /**
     * Parses a CertManagerConfig from a configuration file (auto-detects XML or JSON by extension / content).
     */
    public static CertManagerConfig loadConfigFromFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("Configuration file not found: " + file);
        }
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xml")) {
            try {
                return XmlConfigParser.parse(file);
            } catch (Exception e) {
                throw new IOException("Failed to parse XML configuration from " + file + ": " + e.getMessage(), e);
            }
        }
        return OBJECT_MAPPER.readValue(file, CertManagerConfig.class);
    }

    /**
     * Parses a CertManagerConfig from InputStream (JSON).
     */
    public static CertManagerConfig loadConfigFromStream(InputStream is) throws IOException {
        return OBJECT_MAPPER.readValue(is, CertManagerConfig.class);
    }
}

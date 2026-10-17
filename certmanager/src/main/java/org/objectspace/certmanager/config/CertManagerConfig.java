package org.objectspace.certmanager.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.objectspace.certmanager.model.Token.DurationDeserializer;

import java.time.Duration;

/**
 * Top-level configuration for certificate loading and TLS protocol settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertManagerConfig {

    @JsonProperty("type")
    private LoaderType type = LoaderType.MINIVAULT;

    @JsonProperty("minivault")
    @JsonAlias({"vault", "miniVault"})
    private MiniVaultConfig vault;

    @JsonProperty("file")
    private FileConfig file;

    @JsonProperty("env")
    private EnvConfig env;

    @JsonProperty("dev")
    private DevConfig dev;

    @JsonProperty("initialtimeout")
    @JsonAlias({"initialTimeout", "initial_timeout"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration initialTimeout = Duration.ofMinutes(1);

    @JsonProperty("tlsminversion")
    @JsonAlias({"tlsMinVersion", "tls_min_version"})
    private String tlsMinVersion = "TLS";

    @JsonProperty("tlsmaxversion")
    @JsonAlias({"tlsMaxVersion", "tls_max_version"})
    private String tlsMaxVersion;

    @JsonProperty("mutual")
    private boolean mutual = false;

    public CertManagerConfig() {
    }

    public LoaderType getType() {
        return type;
    }

    public void setType(LoaderType type) {
        this.type = type;
    }

    public MiniVaultConfig getVault() {
        return vault;
    }

    public void setVault(MiniVaultConfig vault) {
        this.vault = vault;
    }

    public FileConfig getFile() {
        return file;
    }

    public void setFile(FileConfig file) {
        this.file = file;
    }

    public EnvConfig getEnv() {
        return env;
    }

    public void setEnv(EnvConfig env) {
        this.env = env;
    }

    public DevConfig getDev() {
        return dev;
    }

    public void setDev(DevConfig dev) {
        this.dev = dev;
    }

    public Duration getInitialTimeout() {
        return initialTimeout;
    }

    public void setInitialTimeout(Duration initialTimeout) {
        this.initialTimeout = initialTimeout;
    }

    public String getTlsMinVersion() {
        return tlsMinVersion;
    }

    public void setTlsMinVersion(String tlsMinVersion) {
        this.tlsMinVersion = tlsMinVersion;
    }

    public String getTlsMaxVersion() {
        return tlsMaxVersion;
    }

    public void setTlsMaxVersion(String tlsMaxVersion) {
        this.tlsMaxVersion = tlsMaxVersion;
    }

    public boolean isMutual() {
        return mutual;
    }

    public void setMutual(boolean mutual) {
        this.mutual = mutual;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CertManagerConfig cfg = new CertManagerConfig();

        public Builder type(LoaderType type) {
            cfg.setType(type);
            return this;
        }

        public Builder vault(MiniVaultConfig vault) {
            cfg.setVault(vault);
            return this;
        }

        public Builder file(FileConfig file) {
            cfg.setFile(file);
            return this;
        }

        public Builder env(EnvConfig env) {
            cfg.setEnv(env);
            return this;
        }

        public Builder dev(DevConfig dev) {
            cfg.setDev(dev);
            return this;
        }

        public Builder initialTimeout(Duration initialTimeout) {
            cfg.setInitialTimeout(initialTimeout);
            return this;
        }

        public Builder tlsMinVersion(String tlsMinVersion) {
            cfg.setTlsMinVersion(tlsMinVersion);
            return this;
        }

        public Builder tlsMaxVersion(String tlsMaxVersion) {
            cfg.setTlsMaxVersion(tlsMaxVersion);
            return this;
        }

        public Builder mutual(boolean mutual) {
            cfg.setMutual(mutual);
            return this;
        }

        public CertManagerConfig build() {
            return cfg;
        }
    }
}

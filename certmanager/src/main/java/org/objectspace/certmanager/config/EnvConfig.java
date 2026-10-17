package org.objectspace.certmanager.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for environment variable certificate loader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvConfig {

    @JsonProperty("certenv")
    @JsonAlias({"certEnv", "cert_env"})
    private String certEnv = "VAULT_CERT";

    @JsonProperty("keyenv")
    @JsonAlias({"keyEnv", "key_env"})
    private String keyEnv = "VAULT_KEY";

    @JsonProperty("caenv")
    @JsonAlias({"caEnv", "ca_env"})
    private String caEnv = "VAULT_CA";

    @JsonProperty("cert")
    private String certContent;

    @JsonProperty("key")
    private String keyContent;

    @JsonProperty("ca")
    private List<String> caContent = new ArrayList<>();

    @JsonProperty("usesystempool")
    @JsonAlias({"useSystemPool", "use_system_pool"})
    private boolean useSystemPool = true;

    public EnvConfig() {
    }

    public String getCertEnv() {
        return certEnv;
    }

    public void setCertEnv(String certEnv) {
        this.certEnv = certEnv;
    }

    public String getKeyEnv() {
        return keyEnv;
    }

    public void setKeyEnv(String keyEnv) {
        this.keyEnv = keyEnv;
    }

    public String getCaEnv() {
        return caEnv;
    }

    public void setCaEnv(String caEnv) {
        this.caEnv = caEnv;
    }

    public String getCertContent() {
        if (certContent != null && !certContent.isBlank()) {
            return certContent;
        }
        if (certEnv != null && !certEnv.isBlank()) {
            return System.getenv(certEnv);
        }
        return null;
    }

    public void setCertContent(String certContent) {
        this.certContent = certContent;
    }

    public String getKeyContent() {
        if (keyContent != null && !keyContent.isBlank()) {
            return keyContent;
        }
        if (keyEnv != null && !keyEnv.isBlank()) {
            return System.getenv(keyEnv);
        }
        return null;
    }

    public void setKeyContent(String keyContent) {
        this.keyContent = keyContent;
    }

    public List<String> getCaContent() {
        if (caContent != null && !caContent.isEmpty()) {
            return caContent;
        }
        if (caEnv != null && !caEnv.isBlank()) {
            String val = System.getenv(caEnv);
            if (val != null && !val.isBlank()) {
                return List.of(val);
            }
        }
        return List.of();
    }

    public void setCaContent(List<String> caContent) {
        this.caContent = caContent;
    }

    public boolean isUseSystemPool() {
        return useSystemPool;
    }

    public void setUseSystemPool(boolean useSystemPool) {
        this.useSystemPool = useSystemPool;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EnvConfig cfg = new EnvConfig();

        public Builder certEnv(String certEnv) {
            cfg.setCertEnv(certEnv);
            return this;
        }

        public Builder keyEnv(String keyEnv) {
            cfg.setKeyEnv(keyEnv);
            return this;
        }

        public Builder caEnv(String caEnv) {
            cfg.setCaEnv(caEnv);
            return this;
        }

        public Builder certContent(String certContent) {
            cfg.setCertContent(certContent);
            return this;
        }

        public Builder keyContent(String keyContent) {
            cfg.setKeyContent(keyContent);
            return this;
        }

        public Builder caContent(List<String> caContent) {
            cfg.setCaContent(caContent != null ? new ArrayList<>(caContent) : new ArrayList<>());
            return this;
        }

        public Builder useSystemPool(boolean useSystemPool) {
            cfg.setUseSystemPool(useSystemPool);
            return this;
        }

        public EnvConfig build() {
            return cfg;
        }
    }
}

package org.objectspace.certmanager.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.objectspace.certmanager.model.Token.DurationDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for filesystem certificate loader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileConfig {

    @JsonProperty("cert")
    @JsonAlias({"certFile", "cert_file"})
    private String certFile;

    @JsonProperty("key")
    @JsonAlias({"keyFile", "key_file"})
    private String keyFile;

    @JsonProperty("interval")
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration interval = Duration.ofMinutes(1);

    @JsonProperty("ca")
    private List<String> ca = new ArrayList<>();

    @JsonProperty("usesystempool")
    @JsonAlias({"useSystemPool", "use_system_pool"})
    private boolean useSystemPool = true;

    public FileConfig() {
    }

    public FileConfig(String certFile, String keyFile) {
        this.certFile = certFile;
        this.keyFile = keyFile;
    }

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public List<String> getCa() {
        return ca;
    }

    public void setCa(List<String> ca) {
        this.ca = ca;
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
        private final FileConfig cfg = new FileConfig();

        public Builder certFile(String certFile) {
            cfg.setCertFile(certFile);
            return this;
        }

        public Builder keyFile(String keyFile) {
            cfg.setKeyFile(keyFile);
            return this;
        }

        public Builder interval(Duration interval) {
            cfg.setInterval(interval);
            return this;
        }

        public Builder ca(List<String> ca) {
            cfg.setCa(ca != null ? new ArrayList<>(ca) : new ArrayList<>());
            return this;
        }

        public Builder useSystemPool(boolean useSystemPool) {
            cfg.setUseSystemPool(useSystemPool);
            return this;
        }

        public FileConfig build() {
            return cfg;
        }
    }
}

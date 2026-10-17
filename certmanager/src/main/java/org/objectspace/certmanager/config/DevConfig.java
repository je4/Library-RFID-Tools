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
 * Configuration for in-memory self-signed development certificates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DevConfig {

    @JsonProperty("commonname")
    @JsonAlias({"commonName", "common_name"})
    private String commonName = "localhost";

    @JsonProperty("dnss")
    @JsonAlias({"dnsNames", "dnsnames"})
    private List<String> dnss = List.of("localhost");

    @JsonProperty("uris")
    private List<String> uris = new ArrayList<>();

    @JsonProperty("ips")
    private List<String> ips = List.of("127.0.0.1", "::1");

    @JsonProperty("validity")
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration validity = Duration.ofDays(30);

    @JsonProperty("interval")
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration interval = Duration.ofDays(10);

    public DevConfig() {
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public List<String> getDnss() {
        return dnss;
    }

    public void setDnss(List<String> dnss) {
        this.dnss = dnss;
    }

    public List<String> getUris() {
        return uris;
    }

    public void setUris(List<String> uris) {
        this.uris = uris;
    }

    public List<String> getIps() {
        return ips;
    }

    public void setIps(List<String> ips) {
        this.ips = ips;
    }

    public Duration getValidity() {
        return validity;
    }

    public void setValidity(Duration validity) {
        this.validity = validity;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DevConfig cfg = new DevConfig();

        public Builder commonName(String commonName) {
            cfg.setCommonName(commonName);
            return this;
        }

        public Builder dnss(List<String> dnss) {
            cfg.setDnss(dnss != null ? new ArrayList<>(dnss) : new ArrayList<>());
            return this;
        }

        public Builder uris(List<String> uris) {
            cfg.setUris(uris != null ? new ArrayList<>(uris) : new ArrayList<>());
            return this;
        }

        public Builder ips(List<String> ips) {
            cfg.setIps(ips != null ? new ArrayList<>(ips) : new ArrayList<>());
            return this;
        }

        public Builder validity(Duration validity) {
            cfg.setValidity(validity);
            return this;
        }

        public Builder interval(Duration interval) {
            cfg.setInterval(interval);
            return this;
        }

        public DevConfig build() {
            return cfg;
        }
    }
}

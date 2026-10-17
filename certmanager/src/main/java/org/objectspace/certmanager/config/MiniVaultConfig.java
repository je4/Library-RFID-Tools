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
 * Configuration for MiniVault loader backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MiniVaultConfig {

    @JsonProperty("baseurl")
    @JsonAlias({"baseUrl", "baseURL", "base_url"})
    private String baseUrl;

    @JsonProperty("parenttoken")
    @JsonAlias({"parentToken", "parent_token"})
    private String parentToken;

    @JsonProperty("tokentype")
    @JsonAlias({"tokenType", "token_type"})
    private String tokenType = "client_cert";

    @JsonProperty("tokenpolicies")
    @JsonAlias({"tokenPolicies", "token_policies"})
    private List<String> tokenPolicies = new ArrayList<>();

    @JsonProperty("tokeninterval")
    @JsonAlias({"tokenInterval", "token_interval"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration tokenInterval;

    @JsonProperty("tokenttl")
    @JsonAlias({"tokenTTL", "tokenTtl", "token_ttl"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration tokenTtl;

    @JsonProperty("certtype")
    @JsonAlias({"certType", "cert_type"})
    private String certType = "client_cert";

    @JsonProperty("uris")
    private List<String> uris = new ArrayList<>();

    @JsonProperty("dnss")
    @JsonAlias({"dnsNames", "dnsnames"})
    private List<String> dnss = new ArrayList<>();

    @JsonProperty("ips")
    private List<String> ips = new ArrayList<>();

    @JsonProperty("certinterval")
    @JsonAlias({"certInterval", "cert_interval"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration certInterval;

    @JsonProperty("certttl")
    @JsonAlias({"certTTL", "certTtl", "cert_ttl"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration certTtl;

    @JsonProperty("ca")
    private List<String> ca = new ArrayList<>();

    @JsonProperty("usesystempool")
    @JsonAlias({"useSystemPool", "use_system_pool"})
    private boolean useSystemPool = true;

    public MiniVaultConfig() {
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Resolves the parent token, supporting "env:VAR_NAME" references.
     */
    public String getParentToken() {
        if (parentToken != null && parentToken.startsWith("env:")) {
            String envVar = parentToken.substring(4).trim();
            String val = System.getenv(envVar);
            return val != null ? val : "";
        }
        return parentToken;
    }

    public void setParentToken(String parentToken) {
        this.parentToken = parentToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public List<String> getTokenPolicies() {
        return tokenPolicies;
    }

    public void setTokenPolicies(List<String> tokenPolicies) {
        this.tokenPolicies = tokenPolicies;
    }

    public Duration getTokenInterval() {
        return tokenInterval;
    }

    public void setTokenInterval(Duration tokenInterval) {
        this.tokenInterval = tokenInterval;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public String getCertType() {
        return certType;
    }

    public void setCertType(String certType) {
        this.certType = certType;
    }

    public List<String> getUris() {
        return uris;
    }

    public void setUris(List<String> uris) {
        this.uris = uris;
    }

    public List<String> getDnss() {
        return dnss;
    }

    public void setDnss(List<String> dnss) {
        this.dnss = dnss;
    }

    public List<String> getIps() {
        return ips;
    }

    public void setIps(List<String> ips) {
        this.ips = ips;
    }

    public Duration getCertInterval() {
        return certInterval;
    }

    public void setCertInterval(Duration certInterval) {
        this.certInterval = certInterval;
    }

    public Duration getCertTtl() {
        return certTtl;
    }

    public void setCertTtl(Duration certTtl) {
        this.certTtl = certTtl;
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
        private final MiniVaultConfig cfg = new MiniVaultConfig();

        public Builder baseUrl(String baseUrl) {
            cfg.setBaseUrl(baseUrl);
            return this;
        }

        public Builder parentToken(String parentToken) {
            cfg.setParentToken(parentToken);
            return this;
        }

        public Builder tokenType(String tokenType) {
            cfg.setTokenType(tokenType);
            return this;
        }

        public Builder tokenPolicies(List<String> tokenPolicies) {
            cfg.setTokenPolicies(tokenPolicies != null ? new ArrayList<>(tokenPolicies) : new ArrayList<>());
            return this;
        }

        public Builder tokenInterval(Duration tokenInterval) {
            cfg.setTokenInterval(tokenInterval);
            return this;
        }

        public Builder tokenTtl(Duration tokenTtl) {
            cfg.setTokenTtl(tokenTtl);
            return this;
        }

        public Builder certType(String certType) {
            cfg.setCertType(certType);
            return this;
        }

        public Builder uris(List<String> uris) {
            cfg.setUris(uris != null ? new ArrayList<>(uris) : new ArrayList<>());
            return this;
        }

        public Builder dnss(List<String> dnss) {
            cfg.setDnss(dnss != null ? new ArrayList<>(dnss) : new ArrayList<>());
            return this;
        }

        public Builder ips(List<String> ips) {
            cfg.setIps(ips != null ? new ArrayList<>(ips) : new ArrayList<>());
            return this;
        }

        public Builder certInterval(Duration certInterval) {
            cfg.setCertInterval(certInterval);
            return this;
        }

        public Builder certTtl(Duration certTtl) {
            cfg.setCertTtl(certTtl);
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

        public MiniVaultConfig build() {
            return cfg;
        }
    }
}

package org.objectspace.wsbrokerclient.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.loader.CertLoader;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * Configuration options for the REST client.
 */
public class RESTConfig {

    @JsonProperty("base_url")
    private String baseUrl = "http://localhost:8080";

    @JsonProperty("bearer")
    private String bearer;

    @JsonProperty("tls")
    private CertManagerConfig certManagerConfig;

    @JsonIgnore
    private CertLoader certLoader;

    @JsonIgnore
    private SSLContext sslContext;

    @JsonProperty("timeout")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration timeout = Duration.ofSeconds(15);

    public RESTConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBearer() {
        return bearer;
    }

    public void setBearer(String bearer) {
        this.bearer = bearer;
    }

    public CertManagerConfig getCertManagerConfig() {
        return certManagerConfig;
    }

    public void setCertManagerConfig(CertManagerConfig certManagerConfig) {
        this.certManagerConfig = certManagerConfig;
    }

    public CertLoader getCertLoader() {
        return certLoader;
    }

    public void setCertLoader(CertLoader certLoader) {
        this.certLoader = certLoader;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public static class Builder {
        private final RESTConfig config = new RESTConfig();

        public Builder baseUrl(String baseUrl) {
            config.setBaseUrl(baseUrl);
            return this;
        }

        public Builder bearer(String bearer) {
            config.setBearer(bearer);
            return this;
        }

        public Builder certManagerConfig(CertManagerConfig certManagerConfig) {
            config.setCertManagerConfig(certManagerConfig);
            return this;
        }

        public Builder certLoader(CertLoader certLoader) {
            config.setCertLoader(certLoader);
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            config.setSslContext(sslContext);
            return this;
        }

        public Builder timeout(Duration timeout) {
            config.setTimeout(timeout);
            return this;
        }

        public RESTConfig build() {
            return config;
        }
    }
}

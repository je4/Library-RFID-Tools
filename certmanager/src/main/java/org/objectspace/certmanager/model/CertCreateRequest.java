package org.objectspace.certmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for /cert/create endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertCreateRequest {

    @JsonProperty("type")
    private String type;

    @JsonProperty("uris")
    private List<String> uris = new ArrayList<>();

    @JsonProperty("dnsnames")
    private List<String> dnsNames = new ArrayList<>();

    @JsonProperty("ips")
    private List<String> ips = new ArrayList<>();

    @JsonProperty("ttl")
    private String ttl;

    public CertCreateRequest() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getUris() {
        return uris;
    }

    public void setUris(List<String> uris) {
        this.uris = uris;
    }

    public List<String> getDnsNames() {
        return dnsNames;
    }

    public void setDnsNames(List<String> dnsNames) {
        this.dnsNames = dnsNames;
    }

    public List<String> getIps() {
        return ips;
    }

    public void setIps(List<String> ips) {
        this.ips = ips;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CertCreateRequest req = new CertCreateRequest();

        public Builder type(String type) {
            req.setType(type);
            return this;
        }

        public Builder uris(List<String> uris) {
            req.setUris(uris != null ? new ArrayList<>(uris) : new ArrayList<>());
            return this;
        }

        public Builder dnsNames(List<String> dnsNames) {
            req.setDnsNames(dnsNames != null ? new ArrayList<>(dnsNames) : new ArrayList<>());
            return this;
        }

        public Builder ips(List<String> ips) {
            req.setIps(ips != null ? new ArrayList<>(ips) : new ArrayList<>());
            return this;
        }

        public Builder ttl(String ttl) {
            req.setTtl(ttl);
            return this;
        }

        public Builder ttl(java.time.Duration ttl) {
            req.setTtl(ttl != null ? org.objectspace.certmanager.util.DurationParser.format(ttl) : null);
            return this;
        }

        public CertCreateRequest build() {
            return req;
        }
    }
}

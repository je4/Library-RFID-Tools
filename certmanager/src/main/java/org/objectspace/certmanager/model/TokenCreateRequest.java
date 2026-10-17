package org.objectspace.certmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request payload for /auth/token/create endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenCreateRequest {

    @JsonProperty("type")
    private TokenType type;

    @JsonProperty("Policies")
    private List<String> policies = new ArrayList<>();

    @JsonProperty("meta")
    private Map<String, String> meta = new HashMap<>();

    @JsonProperty("ttl")
    private String ttl;

    @JsonProperty("maxttl")
    private String maxTtl;

    @JsonProperty("certmaxttl")
    private String certMaxTtl;

    @JsonProperty("renewable")
    private Boolean renewable;

    public TokenCreateRequest() {
    }

    public TokenType getType() {
        return type;
    }

    public void setType(TokenType type) {
        this.type = type;
    }

    public List<String> getPolicies() {
        return policies;
    }

    public void setPolicies(List<String> policies) {
        this.policies = policies;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public String getMaxTtl() {
        return maxTtl;
    }

    public void setMaxTtl(String maxTtl) {
        this.maxTtl = maxTtl;
    }

    public String getCertMaxTtl() {
        return certMaxTtl;
    }

    public void setCertMaxTtl(String certMaxTtl) {
        this.certMaxTtl = certMaxTtl;
    }

    public Boolean getRenewable() {
        return renewable;
    }

    public void setRenewable(Boolean renewable) {
        this.renewable = renewable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final TokenCreateRequest req = new TokenCreateRequest();

        public Builder type(TokenType type) {
            req.setType(type);
            return this;
        }

        public Builder policies(List<String> policies) {
            req.setPolicies(policies != null ? new ArrayList<>(policies) : new ArrayList<>());
            return this;
        }

        public Builder meta(Map<String, String> meta) {
            req.setMeta(meta != null ? new HashMap<>(meta) : new HashMap<>());
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

        public Builder maxTtl(String maxTtl) {
            req.setMaxTtl(maxTtl);
            return this;
        }

        public Builder certMaxTtl(String certMaxTtl) {
            req.setCertMaxTtl(certMaxTtl);
            return this;
        }

        public Builder renewable(Boolean renewable) {
            req.setRenewable(renewable);
            return this;
        }

        public TokenCreateRequest build() {
            return req;
        }
    }
}

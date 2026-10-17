package org.objectspace.certmanager.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.objectspace.certmanager.util.DurationParser;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token representation from MiniVault /auth/token/get.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Token {

    @JsonProperty("type")
    private TokenType type;

    @JsonProperty("expiration")
    private Instant expiration;

    @JsonProperty("policies")
    private List<String> policies = new ArrayList<>();

    @JsonProperty("parent")
    private String parent;

    @JsonProperty("metadata")
    private Map<String, String> metadata = new HashMap<>();

    @JsonProperty("maxttl")
    @JsonAlias({"maxTTL", "MaxTTL", "max_ttl"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration maxTtl = Duration.ZERO;

    @JsonProperty("certmaxttl")
    @JsonAlias({"certMaxTTL", "CertMaxTTL", "cert_max_ttl"})
    @JsonDeserialize(using = DurationDeserializer.class)
    private Duration certMaxTtl = Duration.ZERO;

    public Token() {
    }

    public Token(TokenType type, Instant expiration, Duration maxTtl, Duration certMaxTtl, List<String> policies, Map<String, String> metadata) {
        this.type = type;
        this.expiration = expiration;
        this.maxTtl = maxTtl;
        this.certMaxTtl = certMaxTtl;
        this.policies = policies != null ? policies : new ArrayList<>();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public TokenType getType() {
        return type;
    }

    public void setType(TokenType type) {
        this.type = type;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }

    public List<String> getPolicies() {
        return policies;
    }

    public void setPolicies(List<String> policies) {
        this.policies = policies;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Duration getMaxTtl() {
        return maxTtl;
    }

    public void setMaxTtl(Duration maxTtl) {
        this.maxTtl = maxTtl;
    }

    public Duration getCertMaxTtl() {
        return certMaxTtl;
    }

    public void setCertMaxTtl(Duration certMaxTtl) {
        this.certMaxTtl = certMaxTtl;
    }

    public static class DurationDeserializer extends JsonDeserializer<Duration> {
        @Override
        public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            return DurationParser.parse(text);
        }
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", expiration=" + expiration +
                ", policies=" + policies +
                ", parent='" + parent + '\'' +
                ", maxTtl=" + maxTtl +
                ", certMaxTtl=" + certMaxTtl +
                '}';
    }
}

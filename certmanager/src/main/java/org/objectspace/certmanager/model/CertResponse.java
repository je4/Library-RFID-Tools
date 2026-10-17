package org.objectspace.certmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Certificate result message returned by MiniVault endpoints (/cert/create, /cert/ca/pem).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertResponse {

    @JsonProperty("cert")
    private String cert;

    @JsonProperty("key")
    private String key;

    @JsonProperty("ca")
    private String ca;

    public CertResponse() {
    }

    public CertResponse(String cert, String key, String ca) {
        this.cert = cert;
        this.key = key;
        this.ca = ca;
    }

    public String getCert() {
        return cert;
    }

    public void setCert(String cert) {
        this.cert = cert;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCa() {
        return ca;
    }

    public void setCa(String ca) {
        this.ca = ca;
    }

    @Override
    public String toString() {
        return "CertResponse{" +
                "cert=" + (cert != null ? "[PRESENT]" : "null") +
                ", key=" + (key != null ? "[PRESENT]" : "null") +
                ", ca=" + (ca != null ? "[PRESENT]" : "null") +
                '}';
    }
}

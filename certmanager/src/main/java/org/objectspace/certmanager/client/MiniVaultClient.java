package org.objectspace.certmanager.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.objectspace.certmanager.model.*;
import org.objectspace.certmanager.util.PemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * High-performance HTTP client for the MiniVault PKI API.
 */
public class MiniVaultClient {

    private static final Logger log = LoggerFactory.getLogger(MiniVaultClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String bearerToken;

    public MiniVaultClient(String baseUrl) {
        this(baseUrl, null, Duration.ofSeconds(30));
    }

    public MiniVaultClient(String baseUrl, SSLContext sslContext, Duration timeout) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(30));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.httpClient = builder.build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public MiniVaultClient(String baseUrl, HttpClient httpClient) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    /**
     * Queries token metadata from MiniVault (GET /auth/token/get).
     *
     * @param token authentication token
     * @return Token metadata
     */
    public Token getToken(String token) {
        Objects.requireNonNull(token, "token cannot be null");
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token/get"))
                .header("X-Vault-Token", token)
                .GET();

        HttpResponse<String> response = executeRequest(reqBuilder.build());
        try {
            return objectMapper.readValue(response.body(), Token.class);
        } catch (Exception e) {
            throw new MiniVaultException("Cannot decode token response: " + e.getMessage(), e);
        }
    }

    /**
     * Issues a child token from MiniVault (POST /auth/token/create).
     *
     * @param parentToken parent token for authentication
     * @param param       creation parameters
     * @return created child token string
     */
    public String createToken(String parentToken, TokenCreateRequest param) {
        Objects.requireNonNull(param, "TokenCreateRequest param cannot be null");
        try {
            String jsonBody = objectMapper.writeValueAsString(param);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/token/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (parentToken != null && !parentToken.isBlank()) {
                reqBuilder.header("X-Vault-Token", parentToken);
            }
            if (bearerToken != null && !bearerToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<String> response = executeRequest(reqBuilder.build());
            return objectMapper.readValue(response.body(), String.class);
        } catch (MiniVaultException e) {
            throw e;
        } catch (Exception e) {
            throw new MiniVaultException("Cannot create token: " + e.getMessage(), e);
        }
    }

    /**
     * Issues an X.509 certificate and private key from MiniVault (POST /cert/create).
     *
     * @param token child token with cert issuance policy
     * @param param certificate creation parameters
     * @return CertResponse with cert, key, and ca in PEM format
     */
    public CertResponse createCert(String token, CertCreateRequest param) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(param, "CertCreateRequest param cannot be null");
        try {
            String jsonBody = objectMapper.writeValueAsString(param);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/cert/create"))
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = executeRequest(req);
            return objectMapper.readValue(response.body(), CertResponse.class);
        } catch (MiniVaultException e) {
            throw e;
        } catch (Exception e) {
            throw new MiniVaultException("Cannot create certificate: " + e.getMessage(), e);
        }
    }

    /**
     * High-level method to request and parse an X.509 certificate and private key.
     */
    public KeyCertPair createKeyCertPair(String token, CertCreateRequest param) {
        CertResponse result = createCert(token, param);
        if (result.getCert() == null || result.getKey() == null) {
            throw new MiniVaultException("CertResponse missing cert or key");
        }
        try {
            return PemUtils.parseKeyCertPair(result.getCert(), result.getKey());
        } catch (Exception e) {
            throw new MiniVaultException("Failed to parse returned certificate/key PEM: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches Root/Intermediate CA certificates from MiniVault (GET /cert/ca/pem).
     */
    public List<X509Certificate> getCA() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/cert/ca/pem"))
                .GET()
                .build();

        HttpResponse<String> response = executeRequest(req);
        try {
            CertResponse certResult = objectMapper.readValue(response.body(), CertResponse.class);
            if (certResult.getCa() == null || certResult.getCa().isBlank()) {
                // If it returned raw PEM directly instead of JSON
                if (response.body().contains("-----BEGIN CERTIFICATE-----")) {
                    return PemUtils.parseCertificates(response.body());
                }
                throw new MiniVaultException("No CA certificate returned by MiniVault");
            }
            return PemUtils.parseCertificates(certResult.getCa());
        } catch (MiniVaultException e) {
            throw e;
        } catch (Exception e) {
            // Check if response is raw PEM
            if (response.body().contains("-----BEGIN CERTIFICATE-----")) {
                try {
                    return PemUtils.parseCertificates(response.body());
                } catch (Exception ignored) {
                }
            }
            throw new MiniVaultException("Failed to decode CA response: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> executeRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                String errorMsg = String.format("MiniVault request failed: %s %s -> HTTP %d - %s",
                        request.method(), request.uri(), code, response.body());
                log.error(errorMsg);
                throw new MiniVaultException(code, response.body(), errorMsg);
            }
            return response;
        } catch (MiniVaultException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MiniVaultException("MiniVault connection error: " + e.getMessage(), e);
        }
    }
}

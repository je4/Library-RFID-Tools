package org.objectspace.wsbrokerclient.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.wsbrokerclient.config.RESTConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * REST client for communicating with WSBroker's HTTP REST endpoints.
 */
public class RESTClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RESTClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient httpClient;
    private final String baseURL;
    private final String apiPrefix;
    private final String bearer;
    private final Duration timeout;
    private final CertLoader ownedCertLoader;

    public RESTClient(RESTConfig config) {
        Objects.requireNonNull(config, "RESTConfig cannot be null");

        this.bearer = config.getBearer();
        this.timeout = config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(15);

        String rootAddress = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl().trim()
                : "http://localhost:8080";

        // Normalize ws/wss to http/https
        if (rootAddress.startsWith("ws://")) {
            rootAddress = "http://" + rootAddress.substring(5);
        } else if (rootAddress.startsWith("wss://")) {
            rootAddress = "https://" + rootAddress.substring(6);
        }

        // Strip trailing slash
        while (rootAddress.endsWith("/")) {
            rootAddress = rootAddress.substring(0, rootAddress.length() - 1);
        }

        if (rootAddress.endsWith("/api/v1")) {
            this.baseURL = rootAddress.substring(0, rootAddress.length() - "/api/v1".length());
            this.apiPrefix = "/api/v1";
        } else {
            this.baseURL = rootAddress;
            this.apiPrefix = "/api/v1";
        }

        // Configure TLS / certmanager
        CertLoader loaderToClose = null;
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(this.timeout);

        if (config.getCertLoader() != null) {
            clientBuilder.sslContext(config.getCertLoader().getSSLContext());
        } else if (config.getCertManagerConfig() != null) {
            CertLoader loader = CertManager.createClientLoader(config.getCertManagerConfig());
            loaderToClose = loader;
            clientBuilder.sslContext(loader.getSSLContext());
        } else if (config.getSslContext() != null) {
            clientBuilder.sslContext(config.getSslContext());
        }

        this.httpClient = clientBuilder.build();
        this.ownedCertLoader = loaderToClose;
    }

    /**
     * Factory method creating a new RESTClient with builder configuration.
     */
    public static RESTClient create(RESTConfig config) {
        return new RESTClient(config);
    }

    /**
     * Checks if the remote WSBroker service is healthy and reachable.
     *
     * @return ping response message (e.g. "pong")
     * @throws IOException          if network communication fails
     * @throws InterruptedException if interrupted during request
     */
    public String ping() throws IOException, InterruptedException {
        return doRequest("GET", "/ping", null, String.class);
    }

    /**
     * Publishes an Event envelope via the WSBroker REST endpoint.
     *
     * @param event the event to publish
     * @return publish response from broker
     * @throws IOException          if communication or serialization fails
     * @throws InterruptedException if interrupted during request
     */
    public PublishResponse publish(Event event) throws IOException, InterruptedException {
        Objects.requireNonNull(event, "Event cannot be null");
        return doRequest("POST", "/publish", event, PublishResponse.class);
    }


    /**
     * Publishes a message payload directly to a specific channel endpoint.
     *
     * @param channel channel name
     * @param source  source identifier
     * @param payload message payload
     * @return publish response
     * @throws IOException          if communication fails
     * @throws InterruptedException if interrupted
     */
    public PublishResponse publishToChannel(String channel, String source, Object payload) throws IOException, InterruptedException {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel cannot be empty");
        }
        Event event = Event.createMessage(source, channel.trim(), payload);
        String path = "/channel/" + urlEncode(channel.trim()) + "/publish";
        return doRequest("POST", path, event, PublishResponse.class);
    }

    /**
     * Retrieves all active channels and their subscriber counts from WSBroker.
     *
     * @return list of channel descriptions
     * @throws IOException          if communication fails
     * @throws InterruptedException if interrupted
     */
    public List<ChannelInfo> getChannels() throws IOException, InterruptedException {
        return doRequestGeneric("GET", "/channels", null, new TypeReference<List<ChannelInfo>>() {});
    }

    /**
     * Retrieves all connected client names from WSBroker.
     *
     * @return list of client names
     * @throws IOException          if communication fails
     * @throws InterruptedException if interrupted
     */
    public List<String> getClients() throws IOException, InterruptedException {
        return doRequestGeneric("GET", "/clients", null, new TypeReference<List<String>>() {});
    }

    /**
     * Retrieves client names subscribed to a specific channel.
     *
     * @param channel channel name
     * @return list of client names
     * @throws IOException          if communication fails
     * @throws InterruptedException if interrupted
     */
    public List<String> getChannelClients(String channel) throws IOException, InterruptedException {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel cannot be empty");
        }
        String path = "/channel/" + urlEncode(channel.trim()) + "/clients";
        return doRequestGeneric("GET", path, null, new TypeReference<List<String>>() {});
    }

    /**
     * Forcefully terminates a connected client session on the WSBroker.
     *
     * @param clientName client name to disconnect
     * @throws IOException          if communication fails
     * @throws InterruptedException if interrupted
     */
    public void disconnectClient(String clientName) throws IOException, InterruptedException {
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalArgumentException("ClientName cannot be empty");
        }
        String path = "/client/" + urlEncode(clientName.trim());
        doRequest("DELETE", path, null, Void.class);
    }

    private <T> T doRequest(String method, String path, Object requestBody, Class<T> responseClass) throws IOException, InterruptedException {
        HttpResponse<String> response = executeHttp(method, path, requestBody);
        String body = response.body();

        if (responseClass == Void.class || responseClass == null) {
            return null;
        }
        if (responseClass == String.class) {
            return responseClass.cast(body != null ? body.trim() : "");
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(body, responseClass);
    }

    private <T> T doRequestGeneric(String method, String path, Object requestBody, TypeReference<T> responseTypeRef) throws IOException, InterruptedException {
        HttpResponse<String> response = executeHttp(method, path, requestBody);
        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(body, responseTypeRef);
    }

    private HttpResponse<String> executeHttp(String method, String path, Object requestBody) throws IOException, InterruptedException {
        String fullUrl = this.baseURL + this.apiPrefix + path;
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(this.timeout)
                .header("Accept", "application/json");

        if (this.bearer != null && !this.bearer.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + this.bearer);
        }

        HttpRequest.BodyPublisher publisher;
        if (requestBody != null) {
            String json = OBJECT_MAPPER.writeValueAsString(requestBody);
            reqBuilder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        } else {
            publisher = HttpRequest.BodyPublishers.noBody();
        }

        switch (method.toUpperCase()) {
            case "GET" -> reqBuilder.GET();
            case "POST" -> reqBuilder.POST(publisher);
            case "PUT" -> reqBuilder.PUT(publisher);
            case "DELETE" -> reqBuilder.DELETE();
            default -> reqBuilder.method(method.toUpperCase(), publisher);
        }

        HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " error from " + fullUrl + ": " + resp.body());
        }
        return resp;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (ownedCertLoader != null) {
            try {
                ownedCertLoader.close();
            } catch (Exception e) {
                log.warn("Error closing owned CertLoader: {}", e.getMessage(), e);
            }
        }
    }
}

package org.objectspace.wsbrokerclient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.RESTConfig;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.rest.RESTClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Main entry point and factory for creating WSBroker WebSocket and REST clients.
 */
public final class WSBroker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private WSBroker() {
    }

    /**
     * Creates and configures a new WSBroker WebSocket client.
     *
     * @param config client configuration
     * @return configured WSBrokerClient instance
     */
    public static WSBrokerClient createClient(WSClientConfig config) {
        return new WSBrokerClient(config);
    }

    /**
     * Creates and configures a new WSBroker REST client.
     *
     * @param config REST client configuration
     * @return configured RESTClient instance
     */
    public static RESTClient createRestClient(RESTConfig config) {
        return new RESTClient(config);
    }

    /**
     * Deserializes a WSClientConfig from a JSON string.
     *
     * @param json JSON content
     * @return deserialized WSClientConfig
     * @throws IOException if parsing fails
     */
    public static WSClientConfig loadClientConfigFromJson(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, WSClientConfig.class);
    }

    /**
     * Deserializes a WSClientConfig from a file.
     *
     * @param file JSON config file
     * @return deserialized WSClientConfig
     * @throws IOException if reading or parsing fails
     */
    public static WSClientConfig loadClientConfigFromFile(File file) throws IOException {
        return OBJECT_MAPPER.readValue(file, WSClientConfig.class);
    }

    /**
     * Deserializes a WSClientConfig from an InputStream.
     *
     * @param inputStream input stream
     * @return deserialized WSClientConfig
     * @throws IOException if reading or parsing fails
     */
    public static WSClientConfig loadClientConfigFromStream(InputStream inputStream) throws IOException {
        return OBJECT_MAPPER.readValue(inputStream, WSClientConfig.class);
    }

    /**
     * Deserializes a RESTConfig from a JSON string.
     *
     * @param json JSON content
     * @return deserialized RESTConfig
     * @throws IOException if parsing fails
     */
    public static RESTConfig loadRestConfigFromJson(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, RESTConfig.class);
    }

    /**
     * Deserializes a RESTConfig from a file.
     *
     * @param file JSON config file
     * @return deserialized RESTConfig
     * @throws IOException if reading or parsing fails
     */
    public static RESTConfig loadRestConfigFromFile(File file) throws IOException {
        return OBJECT_MAPPER.readValue(file, RESTConfig.class);
    }
}

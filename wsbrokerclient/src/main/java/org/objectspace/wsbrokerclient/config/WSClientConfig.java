package org.objectspace.wsbrokerclient.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.loader.CertLoader;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration options for the WebSocket client.
 */
public class WSClientConfig {

    @JsonProperty("endpoint")
    private String endpoint = "ws://localhost:8080/ws";

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("subprotocols")
    private List<String> subprotocols = new ArrayList<>();

    @JsonProperty("bearer")
    private String bearer;

    @JsonProperty("tls")
    private CertManagerConfig certManagerConfig;

    @JsonIgnore
    private CertLoader certLoader;

    @JsonIgnore
    private SSLContext sslContext;

    @JsonProperty("ping_interval")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration pingInterval = Duration.ofSeconds(30);

    @JsonProperty("write_timeout")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration writeTimeout = Duration.ofSeconds(5);

    @JsonProperty("connect_timeout")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration connectTimeout = Duration.ofSeconds(10);

    @JsonProperty("reconnect_initial_delay")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration reconnectInitialDelay = Duration.ofSeconds(1);

    @JsonProperty("reconnect_max_delay")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = DurationDeserializer.class)
    private Duration reconnectMaxDelay = Duration.ofSeconds(30);

    @JsonProperty("reconnect_multiplier")
    private double reconnectMultiplier = 1.5;

    @JsonProperty("auto_reconnect")
    private boolean autoReconnect = true;

    @JsonProperty("actions")
    private List<String> actions = new ArrayList<>();

    @JsonProperty("channels")
    private List<String> channels = new ArrayList<>();

    @JsonProperty("buffer_capacity")
    private int bufferCapacity = 256;

    public WSClientConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public void setSubprotocols(List<String> subprotocols) {
        this.subprotocols = subprotocols != null ? subprotocols : new ArrayList<>();
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

    public Duration getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(Duration pingInterval) {
        this.pingInterval = pingInterval;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReconnectInitialDelay() {
        return reconnectInitialDelay;
    }

    public void setReconnectInitialDelay(Duration reconnectInitialDelay) {
        this.reconnectInitialDelay = reconnectInitialDelay;
    }

    public Duration getReconnectMaxDelay() {
        return reconnectMaxDelay;
    }

    public void setReconnectMaxDelay(Duration reconnectMaxDelay) {
        this.reconnectMaxDelay = reconnectMaxDelay;
    }

    public double getReconnectMultiplier() {
        return reconnectMultiplier;
    }

    public void setReconnectMultiplier(double reconnectMultiplier) {
        this.reconnectMultiplier = reconnectMultiplier;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels != null ? channels : new ArrayList<>();
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public void setBufferCapacity(int bufferCapacity) {
        this.bufferCapacity = bufferCapacity;
    }

    public static class Builder {
        private final WSClientConfig config = new WSClientConfig();

        public Builder endpoint(String endpoint) {
            config.setEndpoint(endpoint);
            return this;
        }

        public Builder clientName(String clientName) {
            config.setClientName(clientName);
            return this;
        }

        public Builder subprotocols(List<String> subprotocols) {
            config.setSubprotocols(subprotocols);
            return this;
        }

        public Builder addSubprotocol(String subprotocol) {
            if (config.subprotocols == null) {
                config.subprotocols = new ArrayList<>();
            }
            config.subprotocols.add(subprotocol);
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

        public Builder pingInterval(Duration pingInterval) {
            config.setPingInterval(pingInterval);
            return this;
        }

        public Builder writeTimeout(Duration writeTimeout) {
            config.setWriteTimeout(writeTimeout);
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            config.setConnectTimeout(connectTimeout);
            return this;
        }

        public Builder reconnectInitialDelay(Duration reconnectInitialDelay) {
            config.setReconnectInitialDelay(reconnectInitialDelay);
            return this;
        }

        public Builder reconnectMaxDelay(Duration reconnectMaxDelay) {
            config.setReconnectMaxDelay(reconnectMaxDelay);
            return this;
        }

        public Builder reconnectMultiplier(double reconnectMultiplier) {
            config.setReconnectMultiplier(reconnectMultiplier);
            return this;
        }

        public Builder autoReconnect(boolean autoReconnect) {
            config.setAutoReconnect(autoReconnect);
            return this;
        }

        public Builder actions(List<String> actions) {
            config.setActions(actions);
            return this;
        }

        public Builder addAction(String action) {
            if (config.actions == null) {
                config.actions = new ArrayList<>();
            }
            config.actions.add(action);
            return this;
        }

        public Builder channels(List<String> channels) {
            config.setChannels(channels);
            return this;
        }

        public Builder addChannel(String channel) {
            if (config.channels == null) {
                config.channels = new ArrayList<>();
            }
            config.channels.add(channel);
            return this;
        }

        public Builder bufferCapacity(int bufferCapacity) {
            config.setBufferCapacity(bufferCapacity);
            return this;
        }

        public WSClientConfig build() {
            return config;
        }
    }
}

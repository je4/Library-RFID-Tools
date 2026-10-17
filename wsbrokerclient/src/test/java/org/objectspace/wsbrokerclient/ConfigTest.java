package org.objectspace.wsbrokerclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.wsbrokerclient.config.RESTConfig;
import org.objectspace.wsbrokerclient.config.WSClientConfig;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTest {

    @Test
    @DisplayName("Test WSClientConfig JSON deserialization and default values")
    void testWSClientConfigJson() throws IOException {
        String json = """
        {
            "endpoint": "wss://broker.example.com:8443/ws",
            "client_name": "rfid-gate-01",
            "subprotocols": ["v1.wsbroker.ub.unibas.ch"],
            "bearer": "secret-auth-token",
            "ping_interval": "15s",
            "write_timeout": "3s",
            "connect_timeout": "5s",
            "reconnect_initial_delay": "2s",
            "reconnect_max_delay": "60s",
            "auto_reconnect": true,
            "actions": ["scan", "inventory"],
            "channels": ["rfid/gates", "library/events"],
            "tls": {
                "type": "DEV",
                "tlsminversion": "TLSv1.3"
            }
        }
        """;

        WSClientConfig config = WSBroker.loadClientConfigFromJson(json);
        assertThat(config.getEndpoint()).isEqualTo("wss://broker.example.com:8443/ws");
        assertThat(config.getClientName()).isEqualTo("rfid-gate-01");
        assertThat(config.getSubprotocols()).containsExactly("v1.wsbroker.ub.unibas.ch");
        assertThat(config.getBearer()).isEqualTo("secret-auth-token");
        assertThat(config.getPingInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.getWriteTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.getReconnectInitialDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getReconnectMaxDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.isAutoReconnect()).isTrue();
        assertThat(config.getActions()).containsExactly("scan", "inventory");
        assertThat(config.getChannels()).containsExactly("rfid/gates", "library/events");
        assertThat(config.getCertManagerConfig()).isNotNull();
        assertThat(config.getCertManagerConfig().getType()).isEqualTo(LoaderType.DEV);
    }

    @Test
    @DisplayName("Test RESTConfig JSON deserialization")
    void testRestConfigJson() throws IOException {
        String json = """
        {
            "base_url": "https://broker.example.com:8443",
            "bearer": "admin-bearer-token",
            "timeout": "10s",
            "tls": {
                "type": "FILE",
                "file": {
                    "cert": "/etc/ssl/client.crt",
                    "key": "/etc/ssl/client.key"
                }
            }
        }
        """;

        RESTConfig config = WSBroker.loadRestConfigFromJson(json);
        assertThat(config.getBaseUrl()).isEqualTo("https://broker.example.com:8443");
        assertThat(config.getBearer()).isEqualTo("admin-bearer-token");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getCertManagerConfig()).isNotNull();
        assertThat(config.getCertManagerConfig().getType()).isEqualTo(LoaderType.FILE);
        assertThat(config.getCertManagerConfig().getFile().getCertFile()).isEqualTo("/etc/ssl/client.crt");
    }
}

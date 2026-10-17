package org.objectspace.wsbrokerclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.certmanager.CertManager;
import org.objectspace.certmanager.config.CertManagerConfig;
import org.objectspace.certmanager.config.DevConfig;
import org.objectspace.certmanager.config.LoaderType;
import org.objectspace.certmanager.loader.CertLoader;
import org.objectspace.wsbrokerclient.client.ConnectionListener;
import org.objectspace.wsbrokerclient.client.ConnectionState;
import org.objectspace.wsbrokerclient.client.WSBrokerClient;
import org.objectspace.wsbrokerclient.config.WSClientConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WSBrokerClientTest {

    @Test
    @DisplayName("Test WSBrokerClient initialization with DevLoader certmanager mTLS")
    void testClientInitializationWithCertManager() {
        CertManagerConfig certConfig = CertManagerConfig.builder()
                .type(LoaderType.DEV)
                .dev(DevConfig.builder().commonName("localhost").build())
                .tlsMinVersion("TLSv1.3")
                .build();

        WSClientConfig config = WSClientConfig.builder()
                .endpoint("wss://localhost:8443/ws")
                .clientName("rfid-reader-01")
                .certManagerConfig(certConfig)
                .pingInterval(Duration.ofSeconds(10))
                .autoReconnect(false)
                .build();

        try (WSBrokerClient client = WSBroker.createClient(config)) {
            assertThat(client.getClientName()).isEqualTo("rfid-reader-01");
            assertThat(client.getState()).isEqualTo(ConnectionState.DISCONNECTED);
            assertThat(client.isConnected()).isFalse();

            // Register handlers
            AtomicBoolean handlerTriggered = new AtomicBoolean(false);
            client.onEvent(evt -> handlerTriggered.set(true));
            client.onType(EventType.CLIENT_MESSAGE, evt -> {});
            client.onChannel("rfid/events", evt -> {});
            client.onAction("scan", evt -> {});

            // Subscriptions
            client.subscribe("rfid/events");
            assertThat(client.getSubscribedChannels()).contains("rfid/events");
            client.unsubscribe("rfid/events");
            assertThat(client.getSubscribedChannels()).doesNotContain("rfid/events");
        }
    }

    @Test
    @DisplayName("Test ConnectionListener registrations and state transitions")
    void testConnectionListeners() {
        WSClientConfig config = WSClientConfig.builder()
                .endpoint("ws://localhost:9999/ws")
                .clientName("test-client")
                .autoReconnect(false)
                .build();

        List<ConnectionState> states = new ArrayList<>();
        ConnectionListener listener = (oldState, newState) -> states.add(newState);

        try (WSBrokerClient client = WSBroker.createClient(config)) {
            client.addConnectionListener(listener);
            assertThat(client.getState()).isEqualTo(ConnectionState.DISCONNECTED);

            // Removing listener
            client.removeConnectionListener(listener);
        }
    }
}

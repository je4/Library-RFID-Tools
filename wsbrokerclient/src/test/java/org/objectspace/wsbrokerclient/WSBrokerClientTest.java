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

    @Test
    @DisplayName("Test message event creation ensures proper source sender name and channel target")
    void testMessageSenderAndTargetIntegrity() {
        Event msg = Event.createMessage("test01", "all", "Hello world");
        assertThat(msg.getType()).isEqualTo("message");
        assertThat(msg.getSource()).isEqualTo("test01");
        assertThat(msg.getTarget()).isEqualTo("all");
        assertThat(msg.getDataAsString()).isEqualTo("Hello world");

        String json = msg.toJson();
        Event deserialized = Event.fromJson(json);
        assertThat(deserialized.getSource()).isEqualTo("test01");
        assertThat(deserialized.getTarget()).isEqualTo("all");
        assertThat(deserialized.getType()).isEqualTo("message");
        assertThat(deserialized.getDataAsString()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("Test queryDeviceMetadata creates valid message event with rfid:get_device_metadata command payload")
    void testQueryDeviceMetadataEvent() {
        Event msg = Event.createMessage("dashboard", "reader-01", java.util.Map.of("event", "rfid:get_device_metadata"));
        assertThat(msg.getType()).isEqualTo("message");
        assertThat(msg.getSource()).isEqualTo("dashboard");
        assertThat(msg.getTarget()).isEqualTo("reader-01");
        assertThat(msg.getData().get("event").asText()).isEqualTo("rfid:get_device_metadata");

        String json = msg.toJson();
        Event deserialized = Event.fromJson(json);
        assertThat(deserialized.getSource()).isEqualTo("dashboard");
        assertThat(deserialized.getTarget()).isEqualTo("reader-01");
        assertThat(deserialized.getType()).isEqualTo("message");
        assertThat(deserialized.getData().get("event").asText()).isEqualTo("rfid:get_device_metadata");
    }

    @Test
    @DisplayName("Test WSBrokerClient.send automatically assigns event ID if missing")
    void testSendEnsuresEventId() {
        WSClientConfig config = WSClientConfig.builder()
                .endpoint("ws://localhost:9999/ws")
                .clientName("test-sender")
                .autoReconnect(false)
                .build();

        try (WSBrokerClient client = WSBroker.createClient(config)) {
            Event eventWithoutId = new Event();
            eventWithoutId.setId(null);
            eventWithoutId.setType(EventType.CLIENT_MESSAGE);
            eventWithoutId.setTarget("target-receiver");

            assertThat(eventWithoutId.getId()).isNull();

            // Calling send will populate id even if ws is disconnected
            client.send(eventWithoutId);

            assertThat(eventWithoutId.getId()).isNotNull().hasSize(32);
            assertThat(eventWithoutId.getSource()).isEqualTo("test-sender");
        }
    }

    @Test
    @DisplayName("Test Event createPong overloads with refId correlation")
    void testSendPongWithRefId() {
        Event pong = Event.createPong("client-1", "client-2", "ping-id-999");
        assertThat(pong.getType()).isEqualTo("pong");
        assertThat(pong.getSource()).isEqualTo("client-1");
        assertThat(pong.getTarget()).isEqualTo("client-2");
        assertThat(pong.getRefId()).isEqualTo("ping-id-999");
        assertThat(pong.getId()).isNotNull().hasSize(32);
    }
}

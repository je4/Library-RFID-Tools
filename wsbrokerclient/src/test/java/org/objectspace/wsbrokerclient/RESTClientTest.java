package org.objectspace.wsbrokerclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.wsbrokerclient.config.RESTConfig;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventType;
import org.objectspace.wsbrokerclient.rest.ChannelInfo;
import org.objectspace.wsbrokerclient.rest.PublishResponse;
import org.objectspace.wsbrokerclient.rest.RESTClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RESTClientTest {

    private HttpServer server;
    private int port;
    private RESTClient restClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/v1/ping", exchange -> {
            byte[] resp = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/api/v1/publish", exchange -> {
            String respJson = """
            {"target":"all-clients","type":"client:message","sent":true,"subscribers":5}
            """;
            byte[] resp = respJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/api/v1/channels", exchange -> {
            String respJson = """
            [
                {"name":"rfid/gates","subscribers":3,"clients":["gate1","gate2","gate3"]},
                {"name":"rfid/sorter","subscribers":1,"clients":["sorter1"]}
            ]
            """;
            byte[] resp = respJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/api/v1/clients", exchange -> {
            String respJson = "[\"client1\",\"client2\",\"client3\"]";
            byte[] resp = respJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/api/v1/channel", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/clients")) {
                String respJson = "[\"gate1\",\"gate2\",\"gate3\"]";
                byte[] resp = respJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            } else if (path.endsWith("/publish")) {
                String respJson = """
                {"target":"rfid/gates","type":"client:message","sent":true,"subscribers":3}
                """;
                byte[] resp = respJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        server.createContext("/api/v1/client/badClient", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.start();

        RESTConfig config = RESTConfig.builder()
                .baseUrl("http://localhost:" + port)
                .bearer("test-token")
                .timeout(Duration.ofSeconds(5))
                .build();
        restClient = WSBroker.createRestClient(config);
    }

    @AfterEach
    void tearDown() {
        if (restClient != null) {
            restClient.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Test RESTClient Ping endpoint")
    void testPing() throws Exception {
        String ping = restClient.ping();
        assertThat(ping).isEqualTo("pong");
    }

    @Test
    @DisplayName("Test RESTClient Publish Event endpoint")
    void testPublishEvent() throws Exception {
        Event event = Event.createMessage("tester", "all-clients", Map.of("data", "sample"));
        PublishResponse response = restClient.publish(event);

        assertThat(response).isNotNull();
        assertThat(response.isSent()).isTrue();
        assertThat(response.getSubscribers()).isEqualTo(5);
        assertThat(response.getTarget()).isEqualTo("all-clients");
    }


    @Test
    @DisplayName("Test RESTClient PublishToChannel endpoint")
    void testPublishToChannel() throws Exception {
        PublishResponse response = restClient.publishToChannel("rfid/gates", "tester", "Gate Open");

        assertThat(response).isNotNull();
        assertThat(response.isSent()).isTrue();
        assertThat(response.getSubscribers()).isEqualTo(3);
        assertThat(response.getTarget()).isEqualTo("rfid/gates");
    }

    @Test
    @DisplayName("Test RESTClient GetChannels endpoint")
    void testGetChannels() throws Exception {
        List<ChannelInfo> channels = restClient.getChannels();
        assertThat(channels).hasSize(2);
        assertThat(channels.get(0).getName()).isEqualTo("rfid/gates");
        assertThat(channels.get(0).getSubscribers()).isEqualTo(3);
        assertThat(channels.get(0).getClients()).containsExactly("gate1", "gate2", "gate3");
    }

    @Test
    @DisplayName("Test RESTClient GetClients endpoint")
    void testGetClients() throws Exception {
        List<String> clients = restClient.getClients();
        assertThat(clients).containsExactly("client1", "client2", "client3");
    }

    @Test
    @DisplayName("Test RESTClient GetChannelClients endpoint")
    void testGetChannelClients() throws Exception {
        List<String> clients = restClient.getChannelClients("rfid/gates");
        assertThat(clients).containsExactly("gate1", "gate2", "gate3");
    }

    @Test
    @DisplayName("Test RESTClient DisconnectClient endpoint")
    void testDisconnectClient() throws Exception {
        restClient.disconnectClient("badClient");
    }
}

package org.objectspace.wsbrokerclient;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.wsbrokerclient.event.Event;
import org.objectspace.wsbrokerclient.event.EventType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    record SamplePayload(String name, int count, boolean active) {}

    @Test
    @DisplayName("Test Event serialization and deserialization with strongly-typed payload")
    void testEventJsonSerialization() {
        SamplePayload payload = new SamplePayload("ItemA", 42, true);
        Event original = Event.builder()
                .source("sensor-1")
                .target("hub-main")
                .type(EventType.CLIENT_MESSAGE)
                .token("tok-12345")
                .data(payload)
                .message("Sensor measurement report")
                .addAction("report")
                .addAction("telemetry")
                .subprotocols(List.of("proto-v1", "proto-v2"))
                .build();

        String json = original.toJson();
        assertThat(json).contains("\"type\":\"client:message\"");
        assertThat(json).contains("\"source\":\"sensor-1\"");
        assertThat(json).contains("\"target\":\"hub-main\"");
        assertThat(json).contains("\"name\":\"ItemA\"");
        assertThat(json).contains("\"count\":42");

        Event deserialized = Event.fromJson(json);
        assertThat(deserialized.getSource()).isEqualTo("sensor-1");
        assertThat(deserialized.getTarget()).isEqualTo("hub-main");
        assertThat(deserialized.getType()).isEqualTo("client:message");
        assertThat(deserialized.getEventType()).isEqualTo(EventType.CLIENT_MESSAGE);
        assertThat(deserialized.isType(EventType.CLIENT_MESSAGE)).isTrue();
        assertThat(deserialized.getToken()).isEqualTo("tok-12345");
        assertThat(deserialized.getMessage()).isEqualTo("Sensor measurement report");
        assertThat(deserialized.getActions()).containsExactly("report", "telemetry");
        assertThat(deserialized.getSubprotocols()).containsExactly("proto-v1", "proto-v2");

        SamplePayload extracted = deserialized.getDataAs(SamplePayload.class);
        assertThat(extracted).isNotNull();
        assertThat(extracted.name()).isEqualTo("ItemA");
        assertThat(extracted.count()).isEqualTo(42);
        assertThat(extracted.active()).isTrue();
    }

    @Test
    @DisplayName("Test Generic collection deserialization via TypeReference")
    void testEventGenericDataDeserialization() {
        Map<String, List<Integer>> dataMap = Map.of(
                "group1", List.of(1, 2, 3),
                "group2", List.of(4, 5, 6)
        );

        Event event = Event.createMessage("sourceNode", "targetGroup", dataMap);
        String json = event.toJson();

        Event parsed = Event.fromJson(json);
        Map<String, List<Integer>> result = parsed.getDataAs(new TypeReference<Map<String, List<Integer>>>() {});

        assertThat(result).isNotNull();
        assertThat(result.get("group1")).containsExactly(1, 2, 3);
        assertThat(result.get("group2")).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("Test factory helper methods for message, ping, pong, action, and broadcast")
    void testEventFactoryMethods() {
        Event ping = Event.createPing("client-A");
        assertThat(ping.getType()).isEqualTo(EventType.CLIENT_PING.getValue());
        assertThat(ping.getSource()).isEqualTo("client-A");
        assertThat(ping.getToken()).isNotBlank();

        Event pong = Event.createPong("client-B", ping.getToken());
        assertThat(pong.getType()).isEqualTo(EventType.CLIENT_PONG.getValue());
        assertThat(pong.getToken()).isEqualTo(ping.getToken());

        Event action = Event.createAction("src", "dst", "openDoor", Map.of("doorId", "D1"));
        assertThat(action.getType()).isEqualTo(EventType.CLIENT_ACTION.getValue());
        assertThat(action.getActions()).containsExactly("openDoor");

        Event broadcast = Event.createBroadcast("src", "All systems go");
        assertThat(broadcast.getType()).isEqualTo(EventType.CLIENT_BROADCAST.getValue());
        assertThat(broadcast.getDataAsString()).contains("All systems go");

        Event status = Event.createStatus("node-1", "OPERATIONAL");
        assertThat(status.getType()).isEqualTo(EventType.CLIENT_STATUS.getValue());
        assertThat(status.getMessage()).isEqualTo("OPERATIONAL");
    }
}

package org.objectspace.wsbrokerclient.demo.wschat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMessageTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Test ChatMessage JSON serialization and deserialization")
    void testJsonRoundtrip() throws Exception {
        ChatMessage msg = new ChatMessage("msg-123", "alice", "intern", "Test content", Instant.now(), true);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("msg-123", "alice", "intern", "Test content");

        ChatMessage parsed = mapper.readValue(json, ChatMessage.class);
        assertThat(parsed.getId()).isEqualTo("msg-123");
        assertThat(parsed.getSender()).isEqualTo("alice");
        assertThat(parsed.getChannel()).isEqualTo("intern");
        assertThat(parsed.getContent()).isEqualTo("Test content");
        assertThat(parsed.isSelf()).isTrue();
        assertThat(parsed.getFormattedTime()).isNotEmpty();
    }
}

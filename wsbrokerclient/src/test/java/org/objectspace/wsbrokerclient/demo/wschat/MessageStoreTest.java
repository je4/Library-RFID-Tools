package org.objectspace.wsbrokerclient.demo.wschat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageStoreTest {

    @Test
    @DisplayName("Test MessageStore initialization, addMessage, and auto self-detection")
    void testMessageStoreOperations() {
        MessageStore store = new MessageStore("test01", List.of("all", "intern"), 10);

        assertThat(store.getClientName()).isEqualTo("test01");
        assertThat(store.getChannels()).containsExactly("all", "intern");
        assertThat(store.getActiveChannel()).isEqualTo("all");
        assertThat(store.getStatus()).isEqualTo("Disconnected");

        AtomicInteger addedCount = new AtomicInteger(0);
        store.addListener(new MessageStore.StoreListener() {
            @Override
            public void onMessageAdded(ChatMessage message) {
                addedCount.incrementAndGet();
            }
        });

        // Add message from another user
        ChatMessage msg1 = new ChatMessage("1", "alice", "all", "Hello world", Instant.now(), false);
        store.addMessage(msg1);

        assertThat(addedCount.get()).isEqualTo(1);
        List<ChatMessage> allMsgs = store.getMessages("all");
        assertThat(allMsgs).hasSize(1);
        assertThat(allMsgs.get(0).isSelf()).isFalse();
        assertThat(allMsgs.get(0).getContent()).isEqualTo("Hello world");

        // Add message from self ("test01")
        ChatMessage msg2 = new ChatMessage("2", "test01", "all", "My reply", Instant.now(), false);
        store.addMessage(msg2);

        assertThat(addedCount.get()).isEqualTo(2);
        allMsgs = store.getMessages("all");
        assertThat(allMsgs).hasSize(2);
        assertThat(allMsgs.get(1).isSelf()).isTrue();

        MessageStore.StoreStats stats = store.getStats();
        assertThat(stats.totalMessages()).isEqualTo(2);
        assertThat(stats.channelCount()).isEqualTo(2);
        assertThat(stats.activeChannel()).isEqualTo("all");
    }

    @Test
    @DisplayName("Test dynamic channel addition and capacity limit eviction")
    void testChannelAdditionAndEviction() {
        MessageStore store = new MessageStore("test01", List.of("general"), 3);

        AtomicBoolean channelAdded = new AtomicBoolean(false);
        store.addListener(new MessageStore.StoreListener() {
            @Override
            public void onChannelAdded(String channel) {
                if ("custom".equals(channel)) {
                    channelAdded.set(true);
                }
            }
        });

        store.addChannel("custom");
        assertThat(channelAdded.get()).isTrue();
        assertThat(store.getChannels()).contains("general", "custom");

        // Add 5 messages into channel with maxMessages=3
        for (int i = 1; i <= 5; i++) {
            store.addMessage(new ChatMessage("id-" + i, "user", "custom", "Msg " + i, Instant.now(), false));
        }

        List<ChatMessage> list = store.getMessages("custom");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).getContent()).isEqualTo("Msg 3");
        assertThat(list.get(1).getContent()).isEqualTo("Msg 4");
        assertThat(list.get(2).getContent()).isEqualTo("Msg 5");
    }
}

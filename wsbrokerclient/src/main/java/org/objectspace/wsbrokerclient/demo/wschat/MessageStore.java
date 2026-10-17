package org.objectspace.wsbrokerclient.demo.wschat;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory message history and channel store.
 * Supports observable change listeners for reactive UI updates.
 */
public class MessageStore {

    public interface StoreListener {
        default void onMessageAdded(ChatMessage message) {}
        default void onChannelAdded(String channel) {}
        default void onActiveChannelChanged(String activeChannel) {}
        default void onStatusChanged(String status) {}
        default void onStoreReset() {}
    }

    public record StoreStats(
            int totalMessages,
            int channelCount,
            String activeChannel,
            String status,
            String clientName
    ) {}

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final String clientName;
    private final Map<String, List<ChatMessage>> messages = new LinkedHashMap<>();
    private final List<String> channels = new ArrayList<>();
    private final int maxMessagesPerChannel;
    private final List<StoreListener> listeners = new CopyOnWriteArrayList<>();

    private String activeChannel = "";
    private String status = "Disconnected";

    public MessageStore(String clientName, List<String> initialChannels, int maxMessagesPerChannel) {
        this.clientName = clientName != null ? clientName : "client";
        this.maxMessagesPerChannel = maxMessagesPerChannel > 0 ? maxMessagesPerChannel : 500;

        if (initialChannels != null) {
            for (String ch : initialChannels) {
                if (ch != null && !ch.isBlank()) {
                    String trimmed = ch.trim();
                    if (!this.channels.contains(trimmed)) {
                        this.channels.add(trimmed);
                        this.messages.put(trimmed, new ArrayList<>());
                    }
                }
            }
        }
        if (!this.channels.isEmpty()) {
            this.activeChannel = this.channels.get(0);
        }
    }

    public void addListener(StoreListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(StoreListener listener) {
        listeners.remove(listener);
    }

    /**
     * Appends a message to the specified channel, trimming oldest messages if capacity is exceeded.
     */
    public void addMessage(ChatMessage msg) {
        if (msg == null) {
            return;
        }
        if (msg.getTimestamp() == null) {
            msg.setTimestamp(Instant.now());
        }
        if (clientName.equalsIgnoreCase(msg.getSender())) {
            msg.setSelf(true);
        }

        String channel = msg.getChannel() != null ? msg.getChannel() : "";
        boolean channelAdded = false;

        lock.writeLock().lock();
        try {
            List<ChatMessage> list = messages.computeIfAbsent(channel, k -> new ArrayList<>());
            list.add(msg);
            if (list.size() > maxMessagesPerChannel) {
                list.remove(0);
            }

            if (!channel.isBlank() && !channels.contains(channel)) {
                channels.add(channel);
                channelAdded = true;
                if (activeChannel.isBlank()) {
                    activeChannel = channel;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (channelAdded) {
            for (StoreListener l : listeners) {
                try {
                    l.onChannelAdded(channel);
                } catch (Exception ignored) {}
            }
        }

        for (StoreListener l : listeners) {
            try {
                l.onMessageAdded(msg);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Returns a copy of messages for a given channel.
     */
    public List<ChatMessage> getMessages(String channel) {
        lock.readLock().lock();
        try {
            List<ChatMessage> list = messages.get(channel);
            if (list == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(list);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns a copy of all registered channels.
     */
    public List<String> getChannels() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(channels);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Registers a new channel if it doesn't already exist.
     */
    public void addChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        String trimmed = channel.trim();
        boolean added = false;

        lock.writeLock().lock();
        try {
            if (!channels.contains(trimmed)) {
                channels.add(trimmed);
                messages.computeIfAbsent(trimmed, k -> new ArrayList<>());
                added = true;
                if (activeChannel.isBlank()) {
                    activeChannel = trimmed;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (added) {
            for (StoreListener l : listeners) {
                try {
                    l.onChannelAdded(trimmed);
                } catch (Exception ignored) {}
            }
        }
    }

    public String getActiveChannel() {
        lock.readLock().lock();
        try {
            return activeChannel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setActiveChannel(String channel) {
        if (channel == null) {
            return;
        }
        String trimmed = channel.trim();
        boolean changed = false;

        lock.writeLock().lock();
        try {
            if (!Objects.equals(activeChannel, trimmed)) {
                activeChannel = trimmed;
                changed = true;
                if (!trimmed.isBlank() && !channels.contains(trimmed)) {
                    channels.add(trimmed);
                    messages.computeIfAbsent(trimmed, k -> new ArrayList<>());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (changed) {
            for (StoreListener l : listeners) {
                try {
                    l.onActiveChannelChanged(trimmed);
                } catch (Exception ignored) {}
            }
        }
    }

    public String getStatus() {
        lock.readLock().lock();
        try {
            return status;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setStatus(String newStatus) {
        if (newStatus == null) return;
        boolean changed = false;

        lock.writeLock().lock();
        try {
            if (!Objects.equals(status, newStatus)) {
                status = newStatus;
                changed = true;
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (changed) {
            for (StoreListener l : listeners) {
                try {
                    l.onStatusChanged(newStatus);
                } catch (Exception ignored) {}
            }
        }
    }

    public String getClientName() {
        return clientName;
    }

    public StoreStats getStats() {
        lock.readLock().lock();
        try {
            int total = 0;
            for (List<ChatMessage> list : messages.values()) {
                total += list.size();
            }
            return new StoreStats(total, channels.size(), activeChannel, status, clientName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all messages from memory while preserving channels.
     */
    public void clearMessages() {
        lock.writeLock().lock();
        try {
            for (List<ChatMessage> list : messages.values()) {
                list.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
        for (StoreListener l : listeners) {
            try {
                l.onStoreReset();
            } catch (Exception ignored) {}
        }
    }
}

package org.objectspace.wsbrokerclient.demo.wschat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a chat message within a WSBroker channel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @JsonProperty("id")
    private String id;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("is_self")
    private boolean isSelf;

    public ChatMessage() {
        this.timestamp = Instant.now();
    }

    public ChatMessage(String id, String sender, String channel, String content, Instant timestamp, boolean isSelf) {
        this.id = id;
        this.sender = sender;
        this.channel = channel;
        this.content = content;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.isSelf = isSelf;
    }

    /**
     * Formats the message timestamp as HH:mm:ss in local system time.
     */
    public String getFormattedTime() {
        if (timestamp == null) {
            return "";
        }
        LocalDateTime localTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
        return localTime.format(TIME_FORMATTER);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public void setSelf(boolean self) {
        isSelf = self;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return isSelf == that.isSelf && Objects.equals(id, that.id) && Objects.equals(sender, that.sender) && Objects.equals(channel, that.channel) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sender, channel, content, isSelf);
    }

    @Override
    public String toString() {
        return "[" + getFormattedTime() + "] " + (sender != null ? sender : "Anonymous") + ": " + content;
    }
}

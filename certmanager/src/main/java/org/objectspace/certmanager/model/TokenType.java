package org.objectspace.certmanager.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Supported token types for MiniVault.
 */
public enum TokenType {
    ROOT("root", "ro"),
    PARENT("parent", "pa"),
    SERVER_CERT("server_cert", "sc"),
    CLIENT_CERT("client_cert", "cc"),
    CLIENT_SERVER_CERT("client_server_cert", "cs"),
    CLIENT_SSH_CERT("client_ssh_cert", "ss");

    private final String value;
    private final String prefix;

    TokenType(String value, String prefix) {
        this.value = value;
        this.prefix = prefix;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getPrefix() {
        return prefix;
    }

    @JsonCreator
    public static TokenType fromValue(String text) {
        if (text == null) {
            return null;
        }
        for (TokenType type : TokenType.values()) {
            if (type.value.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text) || type.prefix.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TokenType: " + text + ". Allowed: " + Arrays.toString(values()));
    }

    @Override
    public String toString() {
        return value;
    }
}

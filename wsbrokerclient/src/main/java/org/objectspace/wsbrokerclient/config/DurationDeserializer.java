package org.objectspace.wsbrokerclient.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.objectspace.certmanager.util.DurationParser;

import java.io.IOException;
import java.time.Duration;

/**
 * Jackson deserializer supporting Go-style ("15s", "1m", "24h") and ISO-8601 duration strings.
 */
public class DurationDeserializer extends JsonDeserializer<Duration> {

    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return Duration.ZERO;
        }
        return DurationParser.parse(text);
    }
}

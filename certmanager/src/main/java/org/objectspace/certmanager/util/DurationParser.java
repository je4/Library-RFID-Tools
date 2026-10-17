package org.objectspace.certmanager.util;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and formatting Go-style and ISO-8601 duration strings.
 * Supports units: h (hours), m (minutes), s (seconds), ms (milliseconds), us/µs (microseconds), ns (nanoseconds).
 */
public final class DurationParser {

    private static final Pattern UNIT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ns|us|µs|ms|s|m|h|d)");

    private DurationParser() {
    }

    /**
     * Parses a duration string (e.g. "24h", "1h30m", "10m", "45s", "100ms", "PT1H", or plain numeric nanoseconds).
     *
     * @param input the input string
     * @return parsed Duration, or Duration.ZERO if null or empty
     */
    public static Duration parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Duration.ZERO;
        }
        String trimmed = input.trim();

        // Check if it's an ISO-8601 duration (e.g. PT1H, P1D)
        if (trimmed.startsWith("P") || trimmed.startsWith("-P") || trimmed.startsWith("+P")) {
            try {
                return Duration.parse(trimmed);
            } catch (Exception ignored) {
            }
        }

        // Check if it's plain numeric (interpreted as nanoseconds, as in Go's json time.Duration serialization)
        if (trimmed.matches("^-?\\d+$")) {
            long nanos = Long.parseLong(trimmed);
            return Duration.ofNanos(nanos);
        }

        Matcher matcher = UNIT_PATTERN.matcher(trimmed.toLowerCase());
        long totalNanos = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);

            long unitNanos = switch (unit) {
                case "d" -> (long) (value * 24 * 3600 * 1_000_000_000L);
                case "h" -> (long) (value * 3600 * 1_000_000_000L);
                case "m" -> (long) (value * 60 * 1_000_000_000L);
                case "s" -> (long) (value * 1_000_000_000L);
                case "ms" -> (long) (value * 1_000_000L);
                case "us", "µs" -> (long) (value * 1_000L);
                case "ns" -> (long) value;
                default -> 0L;
            };
            totalNanos += unitNanos;
        }

        if (found) {
            return Duration.ofNanos(totalNanos);
        }

        throw new IllegalArgumentException("Unable to parse duration string: '" + input + "'");
    }

    /**
     * Formats a Duration to a concise Go-style string (e.g. "24h", "1h30m", "10m", "45s").
     */
    public static String format(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        long seconds = duration.toSeconds();
        long nanos = duration.toNanosPart();

        if (seconds == 0 && nanos > 0) {
            if (nanos % 1_000_000 == 0) {
                return (nanos / 1_000_000) + "ms";
            }
            if (nanos % 1_000 == 0) {
                return (nanos / 1_000) + "us";
            }
            return nanos + "ns";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
        }
        if (secs > 0 || sb.isEmpty()) {
            sb.append(secs).append("s");
        }
        return sb.toString();
    }
}

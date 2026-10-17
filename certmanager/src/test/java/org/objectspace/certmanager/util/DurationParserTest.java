package org.objectspace.certmanager.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @Test
    void testParseSimpleUnits() {
        assertThat(DurationParser.parse("24h")).isEqualTo(Duration.ofHours(24));
        assertThat(DurationParser.parse("30m")).isEqualTo(Duration.ofMinutes(30));
        assertThat(DurationParser.parse("45s")).isEqualTo(Duration.ofSeconds(45));
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("100us")).isEqualTo(Duration.ofNanos(100_000));
        assertThat(DurationParser.parse("1000ns")).isEqualTo(Duration.ofNanos(1000));
    }

    @Test
    void testParseCombinedUnits() {
        assertThat(DurationParser.parse("1h30m")).isEqualTo(Duration.ofMinutes(90));
        assertThat(DurationParser.parse("2h15m30s")).isEqualTo(Duration.ofHours(2).plusMinutes(15).plusSeconds(30));
    }

    @Test
    void testParseNanosecondsInt() {
        assertThat(DurationParser.parse("9554545456")).isEqualTo(Duration.ofNanos(9554545456L));
    }

    @Test
    void testParseIso8601() {
        assertThat(DurationParser.parse("PT1H30M")).isEqualTo(Duration.ofMinutes(90));
        assertThat(DurationParser.parse("PT45S")).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void testParseNullOrEmpty() {
        assertThat(DurationParser.parse(null)).isEqualTo(Duration.ZERO);
        assertThat(DurationParser.parse("")).isEqualTo(Duration.ZERO);
        assertThat(DurationParser.parse("   ")).isEqualTo(Duration.ZERO);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> DurationParser.parse("invalid_duration"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFormat() {
        assertThat(DurationParser.format(Duration.ofHours(24))).isEqualTo("24h");
        assertThat(DurationParser.format(Duration.ofHours(1).plusMinutes(30))).isEqualTo("1h30m");
        assertThat(DurationParser.format(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(DurationParser.format(Duration.ofMillis(500))).isEqualTo("500ms");
        assertThat(DurationParser.format(Duration.ZERO)).isEqualTo("0s");
    }
}

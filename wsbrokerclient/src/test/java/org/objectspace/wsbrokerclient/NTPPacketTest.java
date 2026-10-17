package org.objectspace.wsbrokerclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectspace.wsbrokerclient.ntp.NTPPacket;
import org.objectspace.wsbrokerclient.ntp.NTPResponse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NTPPacketTest {

    @Test
    @DisplayName("Test NTP 64-bit timestamp encoding and decoding")
    void testNtpTimestampConversion() {
        Instant now = Instant.parse("2026-10-17T14:30:00.123456789Z");
        long encoded = NTPPacket.encodeNtpTimestamp(now);
        Instant decoded = NTPPacket.decodeNtpTimestamp(encoded);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getEpochSecond()).isEqualTo(now.getEpochSecond());
        // Precision within 1 microsecond due to 32-bit fixed point fractional precision
        assertThat(decoded.getNano()).isCloseTo(now.getNano(), within(1000));
    }

    @Test
    @DisplayName("Test 48-byte NTP packet encode and decode roundtrip")
    void testNtpPacketEncodeDecodeRoundtrip() {
        NTPPacket packet = new NTPPacket();
        packet.setVersion(4);
        packet.setMode(4); // Server response
        packet.setStratum(1);
        packet.setPoll(6);
        packet.setPrecision((byte) -20);
        packet.setLeapIndicator(0);
        packet.setRootDelay(0.0015);
        packet.setRootDispersion(0.0025);
        packet.setReferenceIdentifier("GPS\0".getBytes());

        Instant ref = Instant.now().minusSeconds(100);
        Instant t1 = Instant.now().minusMillis(50);
        Instant t2 = Instant.now().minusMillis(30);
        Instant t3 = Instant.now().minusMillis(28);

        packet.setReferenceTimestamp(ref);
        packet.setOriginateTimestamp(t1);
        packet.setReceiveTimestamp(t2);
        packet.setTransmitTimestamp(t3);

        byte[] encoded = packet.encode();
        assertThat(encoded).hasSize(48);

        NTPPacket decoded = NTPPacket.decode(encoded);
        assertThat(decoded.getVersion()).isEqualTo(4);
        assertThat(decoded.getMode()).isEqualTo(4);
        assertThat(decoded.getStratum()).isEqualTo(1);
        assertThat(decoded.getLeapIndicator()).isEqualTo(0);
        assertThat(decoded.getReferenceIdentifierAsString()).isEqualTo("GPS");

        Instant t4 = Instant.now().minusMillis(8);
        NTPResponse response = new NTPResponse(decoded, t4);

        assertThat(response.isSynchronized()).isTrue();
        assertThat(response.getStratum()).isEqualTo(1);
        assertThat(response.getReferenceIdentifier()).isEqualTo("GPS");

        // Verify offset and delay are calculated
        assertThat(response.getOffset()).isNotNull();
        assertThat(response.getRoundTripDelay()).isNotNull();
        assertThat(response.getRoundTripDelayMillis()).isGreaterThan(0.0);
    }
}

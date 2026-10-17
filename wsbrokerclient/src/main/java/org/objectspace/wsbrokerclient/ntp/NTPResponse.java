package org.objectspace.wsbrokerclient.ntp;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Result of an NTP synchronization query containing the calculated clock offset and round-trip delay.
 */
public class NTPResponse {

    private final NTPPacket packet;
    private final Instant originateTime;    // T1
    private final Instant receiveTime;      // T2
    private final Instant transmitTime;     // T3
    private final Instant destinationTime;  // T4
    private final Duration offset;          // Clock offset
    private final Duration roundTripDelay;  // Network latency / round-trip delay

    public NTPResponse(NTPPacket packet, Instant destinationTime) {
        this.packet = Objects.requireNonNull(packet, "NTPPacket cannot be null");
        this.destinationTime = destinationTime != null ? destinationTime : Instant.now();
        this.originateTime = packet.getOriginateTimestamp();
        this.receiveTime = packet.getReceiveTimestamp();
        this.transmitTime = packet.getTransmitTimestamp();

        if (originateTime != null && receiveTime != null && transmitTime != null && this.destinationTime != null) {
            // T1 to nanos, T2 to nanos, T3 to nanos, T4 to nanos
            long t1Nanos = toNanos(originateTime);
            long t2Nanos = toNanos(receiveTime);
            long t3Nanos = toNanos(transmitTime);
            long t4Nanos = toNanos(this.destinationTime);

            // Offset = ((T2 - T1) + (T3 - T4)) / 2
            long offsetNanos = ((t2Nanos - t1Nanos) + (t3Nanos - t4Nanos)) / 2;
            this.offset = Duration.ofNanos(offsetNanos);

            // Delay = (T4 - T1) - (T3 - T2)
            long delayNanos = (t4Nanos - t1Nanos) - (t3Nanos - t2Nanos);
            this.roundTripDelay = Duration.ofNanos(Math.max(0, delayNanos));
        } else {
            this.offset = Duration.ZERO;
            this.roundTripDelay = Duration.ZERO;
        }
    }

    private static long toNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    public NTPPacket getPacket() {
        return packet;
    }

    public Instant getOriginateTime() {
        return originateTime;
    }

    public Instant getReceiveTime() {
        return receiveTime;
    }

    public Instant getTransmitTime() {
        return transmitTime;
    }

    public Instant getDestinationTime() {
        return destinationTime;
    }

    /**
     * Gets the calculated clock offset between local system and remote NTP server.
     * Positive offset means local clock is behind the server clock.
     *
     * @return clock offset Duration
     */
    public Duration getOffset() {
        return offset;
    }

    /**
     * Gets the clock offset in milliseconds as a floating point number.
     *
     * @return offset in ms
     */
    public double getOffsetMillis() {
        return (double) offset.toNanos() / 1_000_000.0;
    }

    /**
     * Gets the network round-trip delay.
     *
     * @return round trip delay Duration
     */
    public Duration getRoundTripDelay() {
        return roundTripDelay;
    }

    /**
     * Gets the round-trip delay in milliseconds as a floating point number.
     *
     * @return round trip delay in ms
     */
    public double getRoundTripDelayMillis() {
        return (double) roundTripDelay.toNanos() / 1_000_000.0;
    }

    public int getStratum() {
        return packet.getStratum();
    }

    public int getLeapIndicator() {
        return packet.getLeapIndicator();
    }

    public String getReferenceIdentifier() {
        return packet.getReferenceIdentifierAsString();
    }

    /**
     * Checks if the response indicates a synchronized server (stratum between 1 and 15 and leap indicator != 3).
     *
     * @return true if synchronized
     */
    public boolean isSynchronized() {
        return packet.getStratum() >= 1 && packet.getStratum() <= 15 && packet.getLeapIndicator() != 3;
    }

    @Override
    public String toString() {
        return "NTPResponse{" +
                "offset=" + getOffsetMillis() + " ms" +
                ", delay=" + getRoundTripDelayMillis() + " ms" +
                ", stratum=" + getStratum() +
                ", refId='" + getReferenceIdentifier() + '\'' +
                ", origTime=" + originateTime +
                ", rxTime=" + receiveTime +
                ", txTime=" + transmitTime +
                ", dstTime=" + destinationTime +
                ", synchronized=" + isSynchronized() +
                '}';
    }
}

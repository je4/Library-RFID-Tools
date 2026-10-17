package org.objectspace.wsbrokerclient.ntp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Encapsulates a standard 48-byte Network Time Protocol (NTP Version 4, RFC 5905) packet.
 */
public class NTPPacket {

    /**
     * Number of seconds between Jan 1 1900 00:00:00 UTC and Jan 1 1970 00:00:00 UTC (Unix epoch).
     */
    public static final long SECONDS_1900_TO_1970 = 2208988800L;

    /**
     * Standard packet size in bytes.
     */
    public static final int PACKET_SIZE = 48;

    private int leapIndicator; // 2 bits (0: no warning, 1: 61 sec, 2: 59 sec, 3: alarm/unsync)
    private int version = 4;   // 3 bits (usually 4)
    private int mode = 3;      // 3 bits (3: client, 4: server, 5: broadcast)
    private int stratum;       // 8 bits (0: unspecified, 1: primary ref, 2-15: secondary)
    private int poll;          // 8 bits signed exponent
    private byte precision;    // 8 bits signed exponent
    private double rootDelay;  // 32-bit fixed point
    private double rootDispersion; // 32-bit fixed point
    private byte[] referenceIdentifier = new byte[4]; // 4 bytes
    private Instant referenceTimestamp;  // 64-bit timestamp
    private Instant originateTimestamp;  // T1 (client transmit)
    private Instant receiveTimestamp;    // T2 (server receive)
    private Instant transmitTimestamp;   // T3 (server transmit)

    public NTPPacket() {
    }

    /**
     * Creates a new client NTP query packet with current originate timestamp (T1).
     *
     * @return a new client query NTPPacket
     */
    public static NTPPacket createClientQuery() {
        NTPPacket packet = new NTPPacket();
        packet.setMode(3); // Client
        packet.setVersion(4);
        packet.setTransmitTimestamp(Instant.now());
        return packet;
    }

    /**
     * Decodes a 48-byte buffer into an NTPPacket instance.
     *
     * @param bytes the raw NTP byte array
     * @return decoded NTPPacket
     * @throws IllegalArgumentException if the buffer size is less than 48 bytes
     */
    public static NTPPacket decode(byte[] bytes) {
        if (bytes == null || bytes.length < PACKET_SIZE) {
            throw new IllegalArgumentException("NTP packet must be at least " + PACKET_SIZE + " bytes, got: " + (bytes != null ? bytes.length : 0));
        }

        NTPPacket packet = new NTPPacket();
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

        byte firstByte = buf.get();
        packet.leapIndicator = (firstByte >> 6) & 0x03;
        packet.version = (firstByte >> 3) & 0x07;
        packet.mode = firstByte & 0x07;

        packet.stratum = buf.get() & 0xFF;
        packet.poll = buf.get() & 0xFF;
        packet.precision = buf.get();

        int rootDelayFixed = buf.getInt();
        packet.rootDelay = fixedPointToDouble(rootDelayFixed);

        int rootDispFixed = buf.getInt();
        packet.rootDispersion = unsignedFixedPointToDouble(rootDispFixed);

        buf.get(packet.referenceIdentifier);

        packet.referenceTimestamp = decodeNtpTimestamp(buf.getLong());
        packet.originateTimestamp = decodeNtpTimestamp(buf.getLong());
        packet.receiveTimestamp = decodeNtpTimestamp(buf.getLong());
        packet.transmitTimestamp = decodeNtpTimestamp(buf.getLong());

        return packet;
    }

    /**
     * Encodes this NTP packet into a 48-byte raw buffer.
     *
     * @return 48-byte array
     */
    public byte[] encode() {
        byte[] bytes = new byte[PACKET_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

        byte firstByte = (byte) (((leapIndicator & 0x03) << 6) | ((version & 0x07) << 3) | (mode & 0x07));
        buf.put(firstByte);
        buf.put((byte) (stratum & 0xFF));
        buf.put((byte) (poll & 0xFF));
        buf.put(precision);

        buf.putInt(doubleToFixedPoint(rootDelay));
        buf.putInt(doubleToUnsignedFixedPoint(rootDispersion));

        if (referenceIdentifier != null && referenceIdentifier.length == 4) {
            buf.put(referenceIdentifier);
        } else {
            buf.put(new byte[4]);
        }

        buf.putLong(encodeNtpTimestamp(referenceTimestamp));
        buf.putLong(encodeNtpTimestamp(originateTimestamp));
        buf.putLong(encodeNtpTimestamp(receiveTimestamp));
        buf.putLong(encodeNtpTimestamp(transmitTimestamp));

        return bytes;
    }

    /**
     * Converts a 64-bit NTP timestamp to an Instant.
     */
    public static Instant decodeNtpTimestamp(long ntpTimestamp) {
        if (ntpTimestamp == 0) {
            return null;
        }
        long seconds = ((ntpTimestamp >>> 32) & 0xFFFFFFFFL) - SECONDS_1900_TO_1970;
        long fraction = ntpTimestamp & 0xFFFFFFFFL;
        long nanos = (fraction * 1_000_000_000L) >>> 32;
        return Instant.ofEpochSecond(seconds, nanos);
    }

    /**
     * Converts an Instant to a 64-bit NTP timestamp.
     */
    public static long encodeNtpTimestamp(Instant instant) {
        if (instant == null) {
            return 0L;
        }
        long seconds = instant.getEpochSecond() + SECONDS_1900_TO_1970;
        long fraction = ((long) instant.getNano() << 32) / 1_000_000_000L;
        return (seconds << 32) | (fraction & 0xFFFFFFFFL);
    }

    private static double fixedPointToDouble(int fixed) {
        return (double) fixed / 65536.0;
    }

    private static int doubleToFixedPoint(double val) {
        return (int) (val * 65536.0);
    }

    private static double unsignedFixedPointToDouble(int fixed) {
        long unsigned = fixed & 0xFFFFFFFFL;
        return (double) unsigned / 65536.0;
    }

    private static int doubleToUnsignedFixedPoint(double val) {
        return (int) (long) (val * 65536.0);
    }

    // --- Getters & Setters ---

    public int getLeapIndicator() {
        return leapIndicator;
    }

    public NTPPacket setLeapIndicator(int leapIndicator) {
        this.leapIndicator = leapIndicator;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public NTPPacket setVersion(int version) {
        this.version = version;
        return this;
    }

    public int getMode() {
        return mode;
    }

    public NTPPacket setMode(int mode) {
        this.mode = mode;
        return this;
    }

    public int getStratum() {
        return stratum;
    }

    public NTPPacket setStratum(int stratum) {
        this.stratum = stratum;
        return this;
    }

    public int getPoll() {
        return poll;
    }

    public NTPPacket setPoll(int poll) {
        this.poll = poll;
        return this;
    }

    public byte getPrecision() {
        return precision;
    }

    public NTPPacket setPrecision(byte precision) {
        this.precision = precision;
        return this;
    }

    public double getRootDelay() {
        return rootDelay;
    }

    public NTPPacket setRootDelay(double rootDelay) {
        this.rootDelay = rootDelay;
        return this;
    }

    public double getRootDispersion() {
        return rootDispersion;
    }

    public NTPPacket setRootDispersion(double rootDispersion) {
        this.rootDispersion = rootDispersion;
        return this;
    }

    public byte[] getReferenceIdentifier() {
        return referenceIdentifier;
    }

    public String getReferenceIdentifierAsString() {
        if (referenceIdentifier == null) {
            return "";
        }
        return new String(referenceIdentifier, StandardCharsets.US_ASCII).trim();
    }

    public NTPPacket setReferenceIdentifier(byte[] referenceIdentifier) {
        this.referenceIdentifier = referenceIdentifier;
        return this;
    }

    public Instant getReferenceTimestamp() {
        return referenceTimestamp;
    }

    public NTPPacket setReferenceTimestamp(Instant referenceTimestamp) {
        this.referenceTimestamp = referenceTimestamp;
        return this;
    }

    public Instant getOriginateTimestamp() {
        return originateTimestamp;
    }

    public NTPPacket setOriginateTimestamp(Instant originateTimestamp) {
        this.originateTimestamp = originateTimestamp;
        return this;
    }

    public Instant getReceiveTimestamp() {
        return receiveTimestamp;
    }

    public NTPPacket setReceiveTimestamp(Instant receiveTimestamp) {
        this.receiveTimestamp = receiveTimestamp;
        return this;
    }

    public Instant getTransmitTimestamp() {
        return transmitTimestamp;
    }

    public NTPPacket setTransmitTimestamp(Instant transmitTimestamp) {
        this.transmitTimestamp = transmitTimestamp;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NTPPacket ntpPacket)) return false;
        return leapIndicator == ntpPacket.leapIndicator &&
                version == ntpPacket.version &&
                mode == ntpPacket.mode &&
                stratum == ntpPacket.stratum &&
                poll == ntpPacket.poll &&
                precision == ntpPacket.precision &&
                Double.compare(rootDelay, ntpPacket.rootDelay) == 0 &&
                Double.compare(rootDispersion, ntpPacket.rootDispersion) == 0 &&
                Arrays.equals(referenceIdentifier, ntpPacket.referenceIdentifier) &&
                Objects.equals(referenceTimestamp, ntpPacket.referenceTimestamp) &&
                Objects.equals(originateTimestamp, ntpPacket.originateTimestamp) &&
                Objects.equals(receiveTimestamp, ntpPacket.receiveTimestamp) &&
                Objects.equals(transmitTimestamp, ntpPacket.transmitTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leapIndicator, version, mode, stratum, poll, precision, rootDelay, rootDispersion,
                Arrays.hashCode(referenceIdentifier), referenceTimestamp, originateTimestamp, receiveTimestamp, transmitTimestamp);
    }

    @Override
    public String toString() {
        return "NTPPacket{" +
                "version=" + version +
                ", mode=" + mode +
                ", stratum=" + stratum +
                ", leapIndicator=" + leapIndicator +
                ", rootDelay=" + rootDelay +
                ", rootDispersion=" + rootDispersion +
                ", refId='" + getReferenceIdentifierAsString() + '\'' +
                ", refTime=" + referenceTimestamp +
                ", origTime=" + originateTimestamp +
                ", rxTime=" + receiveTimestamp +
                ", txTime=" + transmitTimestamp +
                '}';
    }
}

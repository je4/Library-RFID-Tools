package org.objectspace.rfid.feig;

import de.feig.fedm.Brm;
import de.feig.fedm.BrmItem;
import de.feig.fedm.BufferInfo;
import de.feig.fedm.ErrorCode;
import de.feig.fedm.ReaderModule;
import de.feig.fedm.TagItem;
import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FeigBufferedGateHardwareTest {
	static final String PREFIX = "feig.gate.test.";
	private static final int BATCH_SIZE = 100;

	@Test
	@EnabledIfSystemProperty(named = PREFIX + "enabled", matches = "true")
	void readBufferedEvents() throws Exception {
		assumeTrue("true".equals(System.getProperty(PREFIX + "enabled")), "Hardware test requires explicit opt-in");
		Settings settings = Settings.fromSystemProperties();
		System.out.println("WARNING: Reading may consume buffered events; SDK device-buffer and acknowledgement semantics are not verified.");
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.type", "ethernet");
		config.setProperty("device.feig.host", settings.host());
		config.setProperty("device.feig.port", settings.port());
		config.setProperty("device.feig.gate.enabled", "true");
		FeigRFID reader = new FeigRFID(config);
		try {
			reader.connect();
			printCounters(reader, "before polling");
			ReaderModule module = reader.getReader();
			Brm brm = module.brm();
			brm.setQueueMaxItemCount(BATCH_SIZE);
			long start = System.nanoTime();
			do {
				BufferInfo info = new BufferInfo();
				checkStatus("read buffer information", brm.readReaderBufferInfo(info), module.lastErrorStatusText());
				if (info.numTags() == 0) {
					System.out.println("Reader buffer is empty.");
				} else {
					int status = brm.readReaderBuffer(Math.min(info.numTags(), BATCH_SIZE));
					checkStatus("read buffered events", status, module.lastErrorStatusText());
					BrmItem item;
					int count = 0;
					while ((item = brm.popItem()) != null) {
						TagItem tag = item.tag();
						if (tag == null || !tag.isValid() || tag.idd() == null || tag.idd().length == 0) {
							throw new IllegalStateException("Buffered event has no valid tag ID");
						}
						String time = item.dateTime() != null && item.dateTime().isValid() ? item.dateTime().toString() : "unavailable";
						String direction = item.direction() != null && item.direction().isValid() ? Integer.toString(item.direction().direction()) : "unavailable";
						System.out.println("Tag ID=" + tag.iddToHexString() + " time=" + time + " direction(raw)=" + direction);
						count++;
					}
					if (count == 0) throw new IllegalStateException("Buffer reported events but SDK returned no queued items; read semantics are unverified");
				}
				long remaining = TimeUnit.MILLISECONDS.toNanos(settings.durationMs()) - (System.nanoTime() - start);
				if (remaining <= 0) break;
				TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(settings.intervalMs())));
			} while (System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(settings.durationMs()));
			printCounters(reader, "after polling");
		} finally {
			try {
				reader.close();
			} finally {
				if (reader.getReader() != null) reader.getReader().close();
			}
		}
	}

	private static void printCounters(FeigRFID reader, String phase) throws Exception {
		var counters = reader.readGateCounters();
		if (counters.isEmpty()) System.out.println("No people counters found (" + phase + ").");
		for (FeigRFID.GateCounter counter : counters) {
			System.out.println("People counter " + phase + " address=" + counter.address()
					+ " detector1Counter1=" + counter.detector1Counter1() + " detector1Counter2=" + counter.detector1Counter2()
					+ " detector2Counter1=" + counter.detector2Counter1() + " detector2Counter2=" + counter.detector2Counter2());
		}
	}

	static void checkStatus(String operation, int status, String detail) {
		if (status != ErrorCode.Ok) throw new IllegalStateException(operation + " failed: status=" + status + " (" + detail + "); command-specific status semantics are unverified");
	}

	record Settings(String host, int port, long durationMs, long intervalMs) {
		static Settings fromSystemProperties() {
			String host = System.getProperty(PREFIX + "host", "10.96.12.18").trim();
			if (host.isEmpty()) throw new IllegalArgumentException(PREFIX + "host must not be empty");
			return new Settings(host, (int) number("port", 10001, 1, 65535),
					number("durationMs", 10000, 1, 3600000), number("intervalMs", 1000, 1, 3600000));
		}

		private static long number(String name, long defaultValue, long min, long max) {
			String key = PREFIX + name;
			String value = System.getProperty(key, Long.toString(defaultValue));
			try {
				long parsed = Long.parseLong(value);
				if (parsed >= min && parsed <= max) return parsed;
			} catch (NumberFormatException ignored) {
				// Report the same parameter-specific error for malformed and out-of-range values.
			}
			throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
		}
	}
}
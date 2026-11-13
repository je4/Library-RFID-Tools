package org.objectspace.rfid.feig;

import de.feig.fedm.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeigBufferedGateSettingsTest {
	@Test
	void validatesParametersBeforeConnecting() {
		for (String[] entry : new String[][] {{"host", " "}, {"port", "0"}, {"port", "65536"},
				{"port", "abc"}, {"durationMs", "0"}, {"durationMs", "3600001"}, {"intervalMs", "-1"}}) {
			String key = FeigBufferedGateHardwareTest.PREFIX + entry[0];
			String previous = System.getProperty(key);
			try {
				System.setProperty(key, entry[1]);
				assertTrue(assertThrows(IllegalArgumentException.class, FeigBufferedGateHardwareTest.Settings::fromSystemProperties).getMessage().contains(key));
			} finally {
				if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
			}
		}
	}

	@Test
	void sdkErrorsAreNotEmptyResults() {
		assertDoesNotThrow(() -> FeigBufferedGateHardwareTest.checkStatus("read", ErrorCode.Ok, "ok"));
		assertTrue(assertThrows(IllegalStateException.class,
				() -> FeigBufferedGateHardwareTest.checkStatus("read", 146, "failure")).getMessage().contains("146"));
	}
}
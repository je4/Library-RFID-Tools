package org.objectspace.rfid.feig;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeigGateTest {
	@Test
	void disabledByDefaultAndOnlyExplicitTrueEnables() {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.type", "tcp");
		FeigRFID reader = new FeigRFID(config);
		assertTrue(assertThrows(IllegalStateException.class, reader::readGateCounters).getMessage().contains("enabled"));
		for (String value : new String[] {"", "false", "yes", "1"}) {
			config.setProperty("device.feig.gate.enabled", value);
			assertTrue(assertThrows(IllegalStateException.class, reader::readGateCounters).getMessage().contains("enabled"));
			assertTrue(assertThrows(IllegalStateException.class, reader::readGateDiagnostics).getMessage().contains("enabled"));
		}
	}

	@Test
	void usbAndDisconnectedReadersAreRejected() {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.gate.enabled", "true");
		ISO15693Feig reader = new ISO15693Feig(config);
		assertTrue(assertThrows(IllegalStateException.class, reader::readGateCounters).getMessage().contains("Ethernet"));
		config.setProperty("device.feig.type", "ethernet");
		assertTrue(assertThrows(IllegalStateException.class, reader::readGateDiagnostics).getMessage().contains("connected"));
	}
}
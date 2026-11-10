package org.objectspace.rfid.feig;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeigNetworkConfigTest {

	@Test
	void networkSkipsAutomaticConfigurationFiles() {
		for (String type : new String[] {"ethernet", "tcp"}) {
			BaseConfiguration config = new BaseConfiguration();
			config.setProperty("device.feig.type", type);
			config.setProperty("device.feig.configfile", "missing-configuration.xml");
			config.setProperty("device.feig.restoreconfig", "missing-restore.xml");
			FeigRFID reader = new FeigRFID(config);
			assertDoesNotThrow(reader::init);
			assertDoesNotThrow(reader::close);
		}
	}

	@Test
	void usbStillChecksConfigurationFiles() {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.configfile", "missing-configuration.xml");
		assertThrows(Exception.class, () -> new FeigRFID(config).init());
	}

	@Test
	void ethernetRequiresHost() {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.type", "ethernet");
		Exception error = assertThrows(Exception.class, () -> new FeigRFID(config).connect());
		assertTrue(error.getMessage().contains("device.feig.host"));
	}

	@Test
	void ethernetRejectsInvalidPort() {
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("device.feig.type", "tcp");
		config.setProperty("device.feig.host", "192.0.2.1");
		config.setProperty("device.feig.port", 70000);
		Exception error = assertThrows(Exception.class, () -> new FeigRFID(config).connect());
		assertTrue(error.getMessage().contains("device.feig.port"));
	}
}
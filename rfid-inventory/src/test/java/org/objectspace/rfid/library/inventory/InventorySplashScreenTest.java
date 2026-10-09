/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * This file is part of RFID Library Tools.
 * 
 * RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.library.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import javax.swing.Icon;
import javax.swing.JLabel;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InventorySplashScreenTest {

	@Test
	@DisplayName("Test that InventorySplashScreen loads SVG logo and company imprint")
	public void testSplashScreenCreation() throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("inventory.theme", "light");

		InventorySplashScreen splash = new InventorySplashScreen(config);
		assertThat(splash.getContentPane()).isNotNull();

		// Verify company imprint is present
		boolean imprintFound = findLabelWithText(splash.getContentPane(), InventorySplashScreen.COMPANY_IMPRINT);
		assertThat(imprintFound).isTrue();

		// Verify title is present
		boolean titleFound = findLabelWithText(splash.getContentPane(), "RFID Library Inventory");
		assertThat(titleFound).isTrue();

		splash.dispose();
	}

	@Test
	@DisplayName("Test loading SVG logo icon with custom dimensions")
	public void testLoadLogoIcon() {
		BaseConfiguration config = new BaseConfiguration();
		Icon icon = InventorySplashScreen.loadLogoIcon(config, 120, 120);

		assertThat(icon).isNotNull();
		assertThat(icon.getIconWidth()).isEqualTo(120);
		assertThat(icon.getIconHeight()).isEqualTo(120);
	}

	@Test
	@DisplayName("Test SVG logo color filter adapts white paths for light and dark modes")
	public void testLogoColorFilter() {
		com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter filter = InventorySplashScreen.createLogoColorFilter();
		assertThat(filter).isNotNull();

		// In Light LAF: white/near-white (#FCFCFC) should map to dark slate (#1E293B = 30, 41, 59)
		com.formdev.flatlaf.FlatLightLaf.setup();
		java.awt.Color lightFiltered = filter.filter(new java.awt.Color(252, 252, 252, 255));
		assertThat(lightFiltered).isNotNull();
		assertThat(lightFiltered.getRed()).isEqualTo(30);
		assertThat(lightFiltered.getGreen()).isEqualTo(41);
		assertThat(lightFiltered.getBlue()).isEqualTo(59);

		// In Dark LAF: white/near-white (#FCFCFC) should remain white (#FCFCFC)
		com.formdev.flatlaf.FlatDarkLaf.setup();
		java.awt.Color darkFiltered = filter.filter(new java.awt.Color(252, 252, 252, 255));
		assertThat(darkFiltered).isNotNull();
		assertThat(darkFiltered.getRed()).isEqualTo(252);
		assertThat(darkFiltered.getGreen()).isEqualTo(252);
		assertThat(darkFiltered.getBlue()).isEqualTo(252);
	}

	@Test
	@DisplayName("Test system dark mode detection and theme setup")
	public void testSystemThemeSetup() {
		// Test setupTheme with dark
		BaseConfiguration darkConfig = new BaseConfiguration();
		darkConfig.setProperty("inventory.theme", "dark");
		InventoryModernFrame.setupTheme(darkConfig);
		assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).isTrue();

		// Test setupTheme with light
		BaseConfiguration lightConfig = new BaseConfiguration();
		lightConfig.setProperty("inventory.theme", "light");
		InventoryModernFrame.setupTheme(lightConfig);
		assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).isFalse();

		// Test setupTheme with system (auto-detect)
		BaseConfiguration systemConfig = new BaseConfiguration();
		systemConfig.setProperty("inventory.theme", "system");
		InventoryModernFrame.setupTheme(systemConfig);
		boolean systemIsDark = InventoryModernFrame.isSystemDarkMode();
		assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).isEqualTo(systemIsDark);
	}

	@Test
	@DisplayName("Test showSplash and close lifecycle")
	public void testShowAndCloseLifecycle() throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return;
		}
		BaseConfiguration config = new BaseConfiguration();
		config.setProperty("inventory.theme", "system");

		InventorySplashScreen splash = InventorySplashScreen.showSplash(config);
		assertThat(splash).isNotNull();
		assertThat(splash.isVisible()).isTrue();

		splash.close();
		Thread.sleep(100);
	}

	private boolean findLabelWithText(Container container, String text) {
		for (Component c : container.getComponents()) {
			if (c instanceof JLabel) {
				JLabel lbl = (JLabel) c;
				if (text.equals(lbl.getText())) {
					return true;
				}
			}
			if (c instanceof Container) {
				if (findLabelWithText((Container) c, text)) {
					return true;
				}
			}
		}
		return false;
	}
}

/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * Based on HAWK RFID Library Tools:
 * Copyright 2015 Center for Information, Media and Technology (ZIMT),
 * HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.io.File;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import org.apache.commons.configuration2.AbstractConfiguration;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Modern startup splash screen displaying the application SVG logo and company imprint
 * ("info-age GmbH, Basel") while hardware components and UI frame are initializing.
 */
public class InventorySplashScreen extends JWindow {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_SVG_PATH = "C:\\Users\\micro\\StudioProjects\\nfcreader\\libraryinventory_icon.svg";
	public static final String CLASSPATH_SVG_RESOURCE = "/libraryinventory_icon.svg";
	public static final String COMPANY_IMPRINT = "info-age GmbH, Basel";

	public InventorySplashScreen(AbstractConfiguration config) {
		initComponents(config);
	}

	/**
	 * Displays the splash screen immediately, ensuring theme initialization and safe EDT execution.
	 *
	 * @param config Application configuration
	 * @return the visible InventorySplashScreen instance
	 */
	public static InventorySplashScreen showSplash(AbstractConfiguration config) {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return null;
		}
		InventoryModernFrame.setupTheme(config);
		final InventorySplashScreen[] holder = new InventorySplashScreen[1];
		try {
			if (SwingUtilities.isEventDispatchThread()) {
				holder[0] = new InventorySplashScreen(config);
				holder[0].setVisible(true);
			} else {
				SwingUtilities.invokeAndWait(() -> {
					holder[0] = new InventorySplashScreen(config);
					holder[0].setVisible(true);
				});
			}
		} catch (Exception e) {
			try {
				holder[0] = new InventorySplashScreen(config);
				holder[0].setVisible(true);
			} catch (Exception ignored) {
			}
		}
		return holder[0];
	}

	private void initComponents(AbstractConfiguration config) {
		boolean isDark = FlatSVGIcon.isDarkLaf();

		Color bg = isDark ? new Color(30, 41, 59) : new Color(255, 255, 255);
		Color borderColor = isDark ? new Color(51, 65, 85) : new Color(203, 213, 225);
		Color titleColor = isDark ? new Color(248, 250, 252) : new Color(30, 41, 59);
		Color imprintColor = isDark ? new Color(148, 163, 184) : new Color(100, 116, 139);
		Color progressBg = isDark ? new Color(51, 65, 85) : new Color(241, 245, 249);
		Color progressFg = new Color(37, 99, 235); // Modern royal blue

		JPanel content = new JPanel() {
			private static final long serialVersionUID = 1L;
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};
		content.setLayout(new BorderLayout());
		content.setBackground(bg);
		content.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(borderColor, 1),
				BorderFactory.createEmptyBorder(28, 36, 22, 36)
		));

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setOpaque(false);

		Icon icon = loadLogoIcon(config, 120, 120);
		JLabel lblIcon;
		if (icon != null) {
			lblIcon = new JLabel(icon);
		} else {
			lblIcon = new JLabel();
		}
		lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel lblTitle = new JLabel("RFID Library Inventory");
		lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
		lblTitle.setForeground(titleColor);

		JLabel lblImprint = new JLabel(COMPANY_IMPRINT);
		lblImprint.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblImprint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		lblImprint.setForeground(imprintColor);

		centerPanel.add(lblIcon);
		centerPanel.add(Box.createVerticalStrut(14));
		centerPanel.add(lblTitle);
		centerPanel.add(Box.createVerticalStrut(4));
		centerPanel.add(lblImprint);

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setPreferredSize(new Dimension(280, 4));
		progressBar.setMaximumSize(new Dimension(280, 4));
		progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
		progressBar.setBorderPainted(false);
		progressBar.setForeground(progressFg);
		progressBar.setBackground(progressBg);

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
		bottomPanel.setOpaque(false);
		bottomPanel.add(Box.createVerticalStrut(18));
		bottomPanel.add(progressBar);

		content.add(centerPanel, BorderLayout.CENTER);
		content.add(bottomPanel, BorderLayout.SOUTH);

		setContentPane(content);
		pack();
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
	}

	/**
	 * Resolves and loads the logo icon, prioritizing SVG files and falling back to standard formats.
	 */
	public static Icon loadLogoIcon(AbstractConfiguration config, int width, int height) {
		// 1. Configured logo file if set in configuration
		String configLogo = config != null ? config.getString("inventory.splash.logo", config.getString("inventory.window.logo", null)) : null;
		if (configLogo != null) {
			File f = new File(configLogo);
			if (f.exists()) {
				Icon ic = loadIconFromFile(f, width, height);
				if (ic != null) {
					return ic;
				}
			}
		}

		// 2. Default SVG file path as specified in requirements
		File defaultSvg = new File(DEFAULT_SVG_PATH);
		if (defaultSvg.exists()) {
			Icon ic = loadIconFromFile(defaultSvg, width, height);
			if (ic != null) {
				return ic;
			}
		}

		// 3. Classpath SVG resource
		try {
			URL url = InventorySplashScreen.class.getResource(CLASSPATH_SVG_RESOURCE);
			if (url != null) {
				FlatSVGIcon svgIcon = new FlatSVGIcon(CLASSPATH_SVG_RESOURCE, width, height, InventorySplashScreen.class.getClassLoader());
				svgIcon.setColorFilter(createLogoColorFilter());
				return svgIcon;
			}
		} catch (Throwable ignored) {
		}

		// 4. Fallback to app.png or app.ico in working directory
		File png = new File("app.png");
		if (png.exists()) {
			Icon ic = loadIconFromFile(png, width, height);
			if (ic != null) {
				return ic;
			}
		}

		return null;
	}

	/**
	 * Creates a dynamic color filter for SVG icons so that white/light vectors
	 * are automatically mapped to high-contrast dark tones when a light UI theme is active.
	 */
	public static FlatSVGIcon.ColorFilter createLogoColorFilter() {
		return new FlatSVGIcon.ColorFilter(color -> {
			if (color == null) {
				return null;
			}
			// If color is near-white or very light
			boolean isNearWhite = color.getRed() >= 210 && color.getGreen() >= 210 && color.getBlue() >= 210;
			if (isNearWhite) {
				if (FlatSVGIcon.isDarkLaf()) {
					// In Dark mode, retain the original white/bright appearance
					return color;
				} else {
					// In Light mode, map white lines to crisp dark slate (#1E293B)
					return new Color(30, 41, 59, color.getAlpha());
				}
			}
			return color;
		});
	}

	private static Icon loadIconFromFile(File file, int width, int height) {
		String name = file.getName().toLowerCase();
		if (name.endsWith(".svg")) {
			try {
				FlatSVGIcon svgIcon = new FlatSVGIcon(file).derive(width, height);
				svgIcon.setColorFilter(createLogoColorFilter());
				return svgIcon;
			} catch (Throwable ignored) {
			}
		}
		try {
			Image img = ImageIO.read(file);
			if (img != null) {
				Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
				return new ImageIcon(scaled);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * Safely hides and disposes the splash screen on the Event Dispatch Thread.
	 */
	public void close() {
		SwingUtilities.invokeLater(() -> {
			try {
				setVisible(false);
				dispose();
			} catch (Exception ignored) {
			}
		});
	}
}

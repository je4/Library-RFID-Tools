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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.library.ISO15693Reader;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import de.feig.fedm.utility.HexConvert;

/**
 * Modern Swing + FlatLaf implementation of the RFID Inventory user interface.
 * Features responsive layout, High-DPI support, dark/light theme, KPI metrics cards,
 * live search filtering, tag inspector, hex dump, CSV export, and hardware monitoring.
 */
public class InventoryModernFrame extends JFrame implements InventoryView {

	private static final long serialVersionUID = 1L;

	private final AbstractConfiguration config;
	private final ISO15693Reader reader;
	private InventoryThread thread;
	private InventoryCallback callback;

	// Data model
	private final List<InventoryItemEntry> itemList = new ArrayList<>();
	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

	// UI Components - Header & Badges
	private JLabel lblHeaderTitle;
	private JLabel lblHeaderSubtitle;
	private JLabel lblReaderStatus;
	private JLabel lblScanStatus;

	// UI Components - KPI Metrics Cards
	private JLabel lblCountUniqueVal;
	private JLabel lblCountTotalVal;
	private JLabel lblLastScannedVal;
	private JLabel lblCurrentMarkerVal;

	// UI Components - Toolbar
	public JTextField tInventoryTag;
	private JToggleButton btnStartStop;
	private JButton btnTestScan;
	private JButton btnExport;
	private JButton btnClear;
	private JTextField txtSearch;

	// UI Components - Main Table
	private JTable table;
	private DefaultTableModel tableModel;
	private TableRowSorter<DefaultTableModel> tableSorter;

	// UI Components - Inspector Details
	private JTextField detSyncStatus;
	private JTextField detBarcode;
	private JTextField detSignature;
	private JTextField detUID;
	private JTextField detManufacturer;
	private JTextField detModel;
	private JTextField detPartInfo;
	private JTextField detIsil;
	private JTextField detCountry;
	private JTextField detUsage;
	private JTextField detCRC;
	private JTextField detMarker;
	private JTextArea detHexDump;

	// UI Components - Live Log & Status Bar
	private JTextArea logArea;
	private JLabel lblStatusBar;
	private JPanel pnlFeedbackIndicator;
	private Timer feedbackTimer;

	private volatile boolean isRunning = false;
	private volatile boolean isDisposed = false;

	public InventoryModernFrame(AbstractConfiguration config, ISO15693Reader reader) {
		super("RFID Library Inventory - info-age GmbH, Basel");
		this.config = config;
		this.reader = reader;

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				handleExit();
			}
		});

		initWindowProperties();
		initComponents();
		updateReaderStatus(reader != null && reader.isConnected(), reader != null ? reader.getDeviceInfo() : null);
		updateScanStatus(false);
	}

	/**
	 * Setup FlatLaf Look and Feel based on configuration.
	 */
	public static void setupTheme(AbstractConfiguration config) {
		String theme = config != null ? config.getString("inventory.theme", "system").trim().toLowerCase() : "system";
		try {
			if ("dark".equals(theme)) {
				FlatDarkLaf.setup();
			} else if ("light".equals(theme)) {
				FlatLightLaf.setup();
			} else {
				// System default / auto-detect
				FlatLightLaf.setup();
			}
		} catch (Exception e) {
			System.err.println("Could not initialize FlatLaf theme: " + e.getMessage());
		}
	}

	private void initWindowProperties() {
		int posX = config != null ? config.getInt("inventory.window.posx", 100) : 100;
		int posY = config != null ? config.getInt("inventory.window.posy", 100) : 100;
		int width = config != null ? config.getInt("inventory.window.width", 1150) : 1150;
		int height = config != null ? config.getInt("inventory.window.height", 720) : 720;

		setLocation(posX, posY);
		setSize(width, height);
		setMinimumSize(new Dimension(850, 550));

		// Set application icons
		try {
			List<Image> icons = new ArrayList<>();
			File pngFile = new File("app.png");
			File icoFile = new File("app.ico");
			if (pngFile.exists()) {
				icons.add(ImageIO.read(pngFile));
			}
			if (icoFile.exists()) {
				icons.add(Toolkit.getDefaultToolkit().getImage(icoFile.getAbsolutePath()));
			}
			if (!icons.isEmpty()) {
				setIconImages(icons);
			}
		} catch (Exception ignored) {
		}
	}

	private void initComponents() {
		JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
		mainPanel.setBackground(UIManager.getColor("Panel.background"));

		// 1. Header
		mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

		// Center container: Metrics + Toolbar + SplitPane
		JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
		centerContainer.add(createMetricsBanner(), BorderLayout.NORTH);

		JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
		contentPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
		contentPanel.add(createToolbarPanel(), BorderLayout.NORTH);
		contentPanel.add(createMainSplitPane(), BorderLayout.CENTER);

		centerContainer.add(contentPanel, BorderLayout.CENTER);
		mainPanel.add(centerContainer, BorderLayout.CENTER);

		// 3. Status Bar
		mainPanel.add(createStatusBarPanel(), BorderLayout.SOUTH);

		setContentPane(mainPanel);
	}

	/**
	 * Modern Header Bar with Title, Subtitle, optional Logo, and Status Pills.
	 */
	private JPanel createHeaderPanel() {
		JPanel header = new JPanel(new BorderLayout(16, 0));
		header.setBackground(new Color(26, 36, 56)); // Dark slate blue
		header.setBorder(new EmptyBorder(12, 16, 12, 16));

		// Left: Optional Logo + Titles
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		leftPanel.setOpaque(false);

		String logoPath = config != null ? config.getString("inventory.window.logo", null) : null;
		if (logoPath != null && new File(logoPath).exists()) {
			try {
				ImageIcon logoIcon = new ImageIcon(logoPath);
				Image scaled = logoIcon.getImage().getScaledInstance(-1, 40, Image.SCALE_SMOOTH);
				leftPanel.add(new JLabel(new ImageIcon(scaled)));
			} catch (Exception ignored) {
			}
		}

		JPanel titlePanel = new JPanel();
		titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
		titlePanel.setOpaque(false);

		lblHeaderTitle = new JLabel("RFID Library Inventory");
		lblHeaderTitle.setFont(lblHeaderTitle.getFont().deriveFont(Font.BOLD, 18f));
		lblHeaderTitle.setForeground(Color.WHITE);

		lblHeaderSubtitle = new JLabel("info-age GmbH, Basel  |  ISO 28560 & Finnish Data Model");
		lblHeaderSubtitle.setFont(lblHeaderSubtitle.getFont().deriveFont(Font.PLAIN, 11f));
		lblHeaderSubtitle.setForeground(new Color(160, 174, 192));

		titlePanel.add(lblHeaderTitle);
		titlePanel.add(Box.createVerticalStrut(2));
		titlePanel.add(lblHeaderSubtitle);
		leftPanel.add(titlePanel);

		// Right: Status Badges (Reader Status + Scan Mode)
		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		rightPanel.setOpaque(false);

		lblScanStatus = createStatusBadge("PAUSIERT", new Color(100, 116, 139), Color.WHITE);
		lblReaderStatus = createStatusBadge("\u25CF Reader nicht verbunden", new Color(220, 38, 38), Color.WHITE);

		rightPanel.add(lblScanStatus);
		rightPanel.add(lblReaderStatus);

		header.add(leftPanel, BorderLayout.WEST);
		header.add(rightPanel, BorderLayout.EAST);
		return header;
	}

	private JLabel createStatusBadge(String text, Color bg, Color fg) {
		JLabel label = new JLabel(text) {
			private static final long serialVersionUID = 1L;
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		label.setOpaque(false);
		label.setBackground(bg);
		label.setForeground(fg);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		label.setBorder(new EmptyBorder(4, 10, 4, 10));
		return label;
	}

	/**
	 * KPI Metrics Banner with 4 sleek summary cards.
	 */
	private JPanel createMetricsBanner() {
		JPanel banner = new JPanel(new GridLayout(1, 4, 10, 0));
		banner.setBorder(new EmptyBorder(10, 12, 4, 12));
		banner.setOpaque(false);

		lblCountUniqueVal = new JLabel("0", SwingConstants.CENTER);
		lblCountTotalVal = new JLabel("0", SwingConstants.CENTER);
		lblLastScannedVal = new JLabel("-", SwingConstants.CENTER);
		lblCurrentMarkerVal = new JLabel("-", SwingConstants.CENTER);

		banner.add(createMetricCard("Erfasste Medien", lblCountUniqueVal, new Color(37, 99, 235))); // Primary Blue
		banner.add(createMetricCard("Scan-Vorg\u00E4nge", lblCountTotalVal, new Color(100, 116, 139))); // Slate
		banner.add(createMetricCard("Zuletzt gescannt", lblLastScannedVal, new Color(16, 185, 129))); // Emerald Green
		banner.add(createMetricCard("Aktueller Standort / Marker", lblCurrentMarkerVal, new Color(245, 158, 11))); // Amber

		return banner;
	}

	private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.putClientProperty(FlatClientProperties.STYLE, "arc: 10; background: $Card.background;");
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
				new EmptyBorder(8, 12, 8, 12)
		));

		JLabel lblTitle = new JLabel(title);
		lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 11f));
		lblTitle.setForeground(UIManager.getColor("Label.disabledForeground"));

		valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 18f));
		valueLabel.setForeground(UIManager.getColor("Label.foreground"));
		valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

		card.add(lblTitle, BorderLayout.NORTH);
		card.add(valueLabel, BorderLayout.CENTER);
		return card;
	}

	/**
	 * Toolbar with Marker Input, Start/Pause, Test-Scan, CSV Export, Clear, and Search Filter.
	 */
	private JPanel createToolbarPanel() {
		JPanel toolbar = new JPanel(new BorderLayout(8, 0));
		toolbar.setOpaque(false);

		// Left Controls
		JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		leftControls.setOpaque(false);

		JLabel lblMarker = new JLabel("Standort / Marker:");
		lblMarker.setFont(lblMarker.getFont().deriveFont(Font.BOLD, 12f));

		tInventoryTag = new JTextField(12);
		tInventoryTag.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "z.B. EG-Sachbuecher");
		tInventoryTag.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

		// Initial marker from config if present
		String initialMarker = config != null ? config.getString("inventory.marker", "") : "";
		if (initialMarker != null && !initialMarker.isEmpty()) {
			tInventoryTag.setText(initialMarker);
			lblCurrentMarkerVal.setText(initialMarker);
		}

		tInventoryTag.getDocument().addDocumentListener(new DocumentListener() {
			private void updateMarker() {
				String txt = tInventoryTag.getText().trim();
				lblCurrentMarkerVal.setText(txt.isEmpty() ? "-" : txt);
				if (isRunning && txt.isEmpty()) {
					toggleScanState();
					if (lblStatusBar != null) {
						lblStatusBar.setText("Scan pausiert: Standort / Marker wurde entfernt.");
					}
				}
			}
			@Override
			public void insertUpdate(DocumentEvent e) { updateMarker(); }
			@Override
			public void removeUpdate(DocumentEvent e) { updateMarker(); }
			@Override
			public void changedUpdate(DocumentEvent e) { updateMarker(); }
		});

		btnStartStop = new JToggleButton("\u25B6  Scan Starten");
		btnStartStop.setFont(btnStartStop.getFont().deriveFont(Font.BOLD, 12f));
		btnStartStop.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
		btnStartStop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnStartStop.addActionListener(e -> toggleScanState());

		btnTestScan = new JButton("\u26A1 Test-Scan");
		btnTestScan.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
		btnTestScan.setToolTipText("Simuliert einen RFID-Scan ohne Hardware (z.B. f\u00FCr Funktionstests)");
		btnTestScan.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnTestScan.addActionListener(e -> triggerTestScan());

		btnExport = new JButton("\u2913 CSV Export");
		btnExport.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
		btnExport.setToolTipText("Inventarliste als Excel-kompatible CSV-Datei speichern");
		btnExport.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnExport.addActionListener(e -> exportToCsv());

		btnClear = new JButton("\uD83D\uDDD1 Liste leeren");
		btnClear.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
		btnClear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnClear.addActionListener(e -> confirmAndClear());

		leftControls.add(lblMarker);
		leftControls.add(tInventoryTag);
		leftControls.add(btnStartStop);
		leftControls.add(btnTestScan);
		leftControls.add(btnExport);
		leftControls.add(btnClear);

		// Right Controls: Quick Search Box
		JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		rightControls.setOpaque(false);

		txtSearch = new JTextField(15);
		txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "\uD83D\uDD0D Live-Suche (Barcode, Signatur, UID)...");
		txtSearch.putClientProperty(FlatClientProperties.STYLE, "arc: 8; showClearButton: true");
		txtSearch.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { applyFilter(); }
			@Override
			public void removeUpdate(DocumentEvent e) { applyFilter(); }
			@Override
			public void changedUpdate(DocumentEvent e) { applyFilter(); }
		});
		rightControls.add(txtSearch);

		toolbar.add(leftControls, BorderLayout.WEST);
		toolbar.add(rightControls, BorderLayout.EAST);
		return toolbar;
	}

	private void applyFilter() {
		String text = txtSearch.getText().trim();
		if (text.isEmpty()) {
			tableSorter.setRowFilter(null);
		} else {
			tableSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
		}
	}

	/**
	 * Main SplitPane: Left/Center Data Table & Right Inspector/Log TabbedPane.
	 */
	private JSplitPane createMainSplitPane() {
		// Table Setup
		String[] columnNames = {
			"Status", "Nr", "Uhrzeit", "Barcode / ID", "Signatur", "Teil", "ISIL / Land", "Typ", "Marker", "RFID UID", "CRC"
		};

		tableModel = new DefaultTableModel(columnNames, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		table = new JTable(tableModel);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(26);
		table.setShowHorizontalLines(true);
		table.setShowVerticalLines(false);
		table.putClientProperty(FlatClientProperties.STYLE, "intercellSpacing: 0,1");

		tableSorter = new TableRowSorter<>(tableModel);
		table.setRowSorter(tableSorter);

		// Column widths
		int[] colWidths = { 55, 45, 70, 120, 130, 60, 100, 45, 90, 140, 65 };
		for (int i = 0; i < colWidths.length && i < table.getColumnCount(); i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
		}

		// Custom cell renderers for Status and CRC
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (!isSelected) {
					int modelRow = table.convertRowIndexToModel(row);
					if (modelRow >= 0 && modelRow < itemList.size()) {
						InventoryItemEntry item = itemList.get(modelRow);
						if (!item.statusOk || "FEHLER".equalsIgnoreCase(item.crcStatus)) {
							c.setBackground(new Color(254, 226, 226)); // Soft light red
						} else {
							c.setBackground(row % 2 == 0 ? UIManager.getColor("Table.background") : UIManager.getColor("Table.alternateRowColor"));
						}
					}
				}
				if (column == 0) {
					setHorizontalAlignment(SwingConstants.CENTER);
					if ("\u2714".equals(value)) {
						setForeground(new Color(22, 163, 74)); // Green checkmark
						setFont(getFont().deriveFont(Font.BOLD));
					} else if ("\u2716".equals(value)) {
						setForeground(new Color(220, 38, 38)); // Red cross
						setFont(getFont().deriveFont(Font.BOLD));
					}
				} else if (column == 1 || column == 5 || column == 7 || column == 10) {
					setHorizontalAlignment(SwingConstants.CENTER);
				} else {
					setHorizontalAlignment(SwingConstants.LEFT);
				}
				return c;
			}
		});

		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				int selectedRow = table.getSelectedRow();
				if (selectedRow >= 0) {
					int modelRow = table.convertRowIndexToModel(selectedRow);
					if (modelRow >= 0 && modelRow < itemList.size()) {
						updateInspector(itemList.get(modelRow));
					}
				}
			}
		});

		JScrollPane tableScroll = new JScrollPane(table);
		tableScroll.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

		// Right TabbedPane (Inspector + Live Log)
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.putClientProperty(FlatClientProperties.STYLE, "tabArc: 8");
		tabbedPane.addTab("Tag Inspector / Details", createInspectorPanel());
		tabbedPane.addTab("Live Log / Konsole", createLogPanel());

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, tabbedPane);
		splitPane.setResizeWeight(0.65);
		splitPane.setDividerSize(6);
		splitPane.setContinuousLayout(true);
		splitPane.setBorder(null);

		return splitPane;
	}

	/**
	 * Inspector details form for selected tag.
	 */
	private JPanel createInspectorPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel form = new JPanel(new GridLayout(11, 2, 8, 6));

		detSyncStatus = createReadOnlyField();
		detBarcode = createReadOnlyField();
		detSignature = createReadOnlyField();
		detUID = createReadOnlyField();
		detManufacturer = createReadOnlyField();
		detModel = createReadOnlyField();
		detPartInfo = createReadOnlyField();
		detIsil = createReadOnlyField();
		detUsage = createReadOnlyField();
		detCRC = createReadOnlyField();
		detMarker = createReadOnlyField();

		addFormRow(form, "Sync / Status:", detSyncStatus);
		addFormRow(form, "Barcode / Medien-ID:", detBarcode);
		addFormRow(form, "Signatur:", detSignature);
		addFormRow(form, "RFID UID:", detUID);
		addFormRow(form, "Hersteller:", detManufacturer);
		addFormRow(form, "Transponder-Typ:", detModel);
		addFormRow(form, "Teile-Info:", detPartInfo);
		addFormRow(form, "ISIL & Land:", detIsil);
		addFormRow(form, "Nutzungsart:", detUsage);
		addFormRow(form, "CRC Pr\u00FCfung:", detCRC);
		addFormRow(form, "Standort / Marker:", detMarker);

		JPanel hexPanel = new JPanel(new BorderLayout(0, 4));
		JLabel lblHex = new JLabel("Rohdaten (Hex Dump):");
		lblHex.setFont(lblHex.getFont().deriveFont(Font.BOLD, 11f));
		detHexDump = new JTextArea(4, 20);
		detHexDump.setEditable(false);
		detHexDump.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane hexScroll = new JScrollPane(detHexDump);

		hexPanel.add(lblHex, BorderLayout.NORTH);
		hexPanel.add(hexScroll, BorderLayout.CENTER);

		panel.add(form, BorderLayout.NORTH);
		panel.add(hexPanel, BorderLayout.CENTER);
		return panel;
	}

	private void addFormRow(JPanel form, String labelText, JTextField field) {
		JLabel label = new JLabel(labelText);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		label.setForeground(UIManager.getColor("Label.disabledForeground"));
		form.add(label);
		form.add(field);
	}

	private JTextField createReadOnlyField() {
		JTextField tf = new JTextField();
		tf.setEditable(false);
		tf.putClientProperty(FlatClientProperties.STYLE, "arc: 6");
		return tf;
	}

	/**
	 * Live Log view with monospaced console and autoscroll.
	 */
	private JPanel createLogPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(new EmptyBorder(8, 8, 8, 8));

		logArea = new JTextArea();
		logArea.setEditable(false);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		JScrollPane scroll = new JScrollPane(logArea);
		panel.add(scroll, BorderLayout.CENTER);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		JButton btnClearLog = new JButton("Log l\u00F6schen");
		btnClearLog.putClientProperty(FlatClientProperties.STYLE, "arc: 6");
		btnClearLog.addActionListener(e -> logArea.setText(""));
		btnPanel.add(btnClearLog);
		panel.add(btnPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Bottom status bar showing recent activity and scan feedback.
	 */
	private JPanel createStatusBarPanel() {
		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBorder(new EmptyBorder(4, 12, 4, 12));
		bar.setBackground(UIManager.getColor("Panel.background"));

		pnlFeedbackIndicator = new JPanel() {
			private static final long serialVersionUID = 1L;
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillOval(2, 2, 10, 10);
				g2.dispose();
			}
		};
		pnlFeedbackIndicator.setPreferredSize(new Dimension(14, 14));
		pnlFeedbackIndicator.setBackground(new Color(156, 163, 175)); // Grey idle

		lblStatusBar = new JLabel("Bereit. Warte auf RFID-Tags...");
		lblStatusBar.setFont(lblStatusBar.getFont().deriveFont(Font.PLAIN, 11f));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		left.add(pnlFeedbackIndicator);
		left.add(lblStatusBar);

		bar.add(left, BorderLayout.WEST);
		return bar;
	}

	public void toggleScanState() {
		if (!isRunning) {
			String marker = getTagInfo();
			if (marker == null || marker.trim().isEmpty()) {
				if (!java.awt.GraphicsEnvironment.isHeadless()) {
					JOptionPane.showMessageDialog(this,
							"Bitte geben Sie zuerst einen Standort / Marker ein, bevor Sie den Scan starten.",
							"Standort / Marker erforderlich",
							JOptionPane.WARNING_MESSAGE);
				}
				if (tInventoryTag != null) {
					tInventoryTag.requestFocusInWindow();
				}
				if (btnStartStop != null) {
					btnStartStop.setSelected(false);
				}
				if (lblStatusBar != null) {
					lblStatusBar.setText("Scan kann nicht gestartet werden: Standort / Marker fehlt.");
				}
				return;
			}
		}
		isRunning = !isRunning;
		updateScanStatus(isRunning);
		if (thread != null) {
			thread.pause(!isRunning);
		}
	}

	private void updateScanStatus(boolean active) {
		this.isRunning = active;
		if (active) {
			btnStartStop.setText("\u23F8  Scan Anhalten");
			btnStartStop.setSelected(true);
			lblScanStatus.setText("AKTIV (SCANNT)");
			lblScanStatus.setBackground(new Color(22, 163, 74)); // Green
		} else {
			btnStartStop.setText("\u25B6  Scan Starten");
			btnStartStop.setSelected(false);
			lblScanStatus.setText("PAUSIERT");
			lblScanStatus.setBackground(new Color(100, 116, 139)); // Slate
		}
		lblScanStatus.repaint();
	}

	public void updateReaderStatus(boolean connected, String deviceInfo) {
		SwingUtilities.invokeLater(() -> {
			if (connected) {
				String info = (deviceInfo != null && !deviceInfo.isEmpty()) ? deviceInfo : "FEIG USB";
				lblReaderStatus.setText("\u25CF Verbunden: " + info);
				lblReaderStatus.setBackground(new Color(22, 163, 74)); // Green
			} else {
				lblReaderStatus.setText("\u25CF Getrennt (Warte auf Leser...)");
				lblReaderStatus.setBackground(new Color(220, 38, 38)); // Red
			}
			lblReaderStatus.repaint();
		});
	}

	private void flashFeedback(boolean success) {
		Color activeColor = success ? new Color(34, 197, 94) : new Color(239, 68, 68);
		pnlFeedbackIndicator.setBackground(activeColor);
		pnlFeedbackIndicator.repaint();

		if (feedbackTimer != null && feedbackTimer.isRunning()) {
			feedbackTimer.stop();
		}
		feedbackTimer = new Timer(500, e -> {
			pnlFeedbackIndicator.setBackground(new Color(156, 163, 175));
			pnlFeedbackIndicator.repaint();
		});
		feedbackTimer.setRepeats(false);
		feedbackTimer.start();
	}

	private void updateInspector(InventoryItemEntry item) {
		if (item == null) return;
		detSyncStatus.setText(item.statusDetails != null ? item.statusDetails : (item.statusOk ? "OK (\u2714)" : "Fehler (\u2716)"));
		detBarcode.setText(item.primaryItemId != null ? item.primaryItemId : "-");
		detSignature.setText(item.signature != null ? item.signature : "-");
		detUID.setText(item.uid != null ? item.uid : "-");
		detManufacturer.setText(item.manufacturer != null ? item.manufacturer : "-");
		detModel.setText(item.tagName != null ? item.tagName : "-");
		detPartInfo.setText(item.partInfo != null ? item.partInfo : "-");
		detIsil.setText(item.isilAndCountry != null ? item.isilAndCountry : "-");
		detUsage.setText(String.valueOf(item.usageType));
		detCRC.setText(item.crcStatus != null ? item.crcStatus : "-");
		detMarker.setText(item.marker != null ? item.marker : "-");

		if (item.rawData != null && item.rawData.length > 0) {
			detHexDump.setText(formatHexDump(item.rawData));
		} else {
			detHexDump.setText("- Keine Rohdaten vorhanden -");
		}
	}

	public static String formatHexDump(byte[] data) {
		if (data == null || data.length == 0) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < data.length; i += 4) {
			sb.append(String.format("Block %02d:  ", i / 4));
			for (int j = 0; j < 4; j++) {
				if (i + j < data.length) {
					sb.append(String.format("%02X ", data[i + j] & 0xFF));
				} else {
					sb.append("   ");
				}
			}
			sb.append("  |");
			for (int j = 0; j < 4 && (i + j) < data.length; j++) {
				byte b = data[i + j];
				sb.append((b >= 32 && b <= 126) ? (char) b : '.');
			}
			sb.append("|\n");
		}
		return sb.toString();
	}

	private void confirmAndClear() {
		int res = JOptionPane.showConfirmDialog(this,
				"M\u00F6chten Sie die aktuelle Inventarliste mit " + itemList.size() + " Eintr\u00E4gen wirklich leeren?",
				"Liste leeren best\u00E4tigen",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (res == JOptionPane.YES_OPTION) {
			clearItems();
		}
	}

	public void clearItems() {
		SwingUtilities.invokeLater(() -> {
			itemList.clear();
			tableModel.setRowCount(0);
			lblCountUniqueVal.setText("0");
			lblCountTotalVal.setText("0");
			lblLastScannedVal.setText("-");
			logArea.setText("");
			clearInspector();
			lblStatusBar.setText("Inventarliste zur\u00FCckgesetzt.");
			if (callback != null) {
				callback.clearUIDList();
			}
		});
	}

	private void clearInspector() {
		detSyncStatus.setText("");
		detBarcode.setText("");
		detSignature.setText("");
		detUID.setText("");
		detManufacturer.setText("");
		detModel.setText("");
		detPartInfo.setText("");
		detIsil.setText("");
		detUsage.setText("");
		detCRC.setText("");
		detMarker.setText("");
		detHexDump.setText("");
	}

	private void exportToCsv() {
		if (itemList.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"Die Inventarliste ist aktuell leer. Es gibt keine Daten zum Exportieren.",
					"Export nicht m\u00F6glich",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Inventarliste als CSV exportieren");
		String defaultName = "RFID_Inventar_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
		fc.setSelectedFile(new File(defaultName));
		fc.setFileFilter(new FileNameExtensionFilter("CSV-Dateien (*.csv)", "csv"));

		if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			String path = file.getAbsolutePath();
			if (!path.toLowerCase().endsWith(".csv")) {
				path += ".csv";
				file = new File(path);
			}

			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
				// Write UTF-8 BOM for Microsoft Excel compatibility
				pw.print('\ufeff');
				pw.println("Status;Nr;Uhrzeit;Barcode_ID;Signatur;Teil_Nr;Teile_Gesamt;ISIL;Land;Nutzungsart;Standort_Marker;RFID_UID;CRC_Status;Hersteller;Transponder_Typ;Status_Details");

				for (InventoryItemEntry item : itemList) {
					pw.printf("\"%s\";%d;\"%s\";\"%s\";\"%s\";%d;%d;\"%s\";\"%s\";%d;\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"%n",
							item.statusSymbol,
							item.index,
							item.time != null ? item.time : "",
							item.primaryItemId != null ? item.primaryItemId.replace("\"", "\"\"") : "",
							item.signature != null ? item.signature.replace("\"", "\"\"") : "",
							item.partNumber,
							item.partsInItem,
							item.isil != null ? item.isil.replace("\"", "\"\"") : "",
							item.country != null ? item.country.replace("\"", "\"\"") : "",
							item.usageType,
							item.marker != null ? item.marker.replace("\"", "\"\"") : "",
							item.uid != null ? item.uid.replace("\"", "\"\"") : "",
							item.crcStatus != null ? item.crcStatus : "",
							item.manufacturer != null ? item.manufacturer.replace("\"", "\"\"") : "",
							item.tagName != null ? item.tagName.replace("\"", "\"\"") : "",
							item.statusDetails != null ? item.statusDetails.replace("\"", "\"\"") : ""
					);
				}

				JOptionPane.showMessageDialog(this,
						"Die Inventarliste mit " + itemList.size() + " Datens\u00E4tzen wurde erfolgreich exportiert:\n" + file.getAbsolutePath(),
						"Export erfolgreich",
						JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this,
						"Fehler beim Speichern der CSV-Datei: " + e.getMessage(),
						"Exportfehler",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void triggerTestScan() {
		if (callback != null) {
			callback.triggerTestScan();
		} else {
			int randomSuffix = 1000 + (int) (Math.random() * 9000);
			String mockUid = String.format("E0040150%04dABCD", randomSuffix);
			String itemId = "3011" + randomSuffix;
			try {
				FinnishDataModel model = new FinnishDataModel();
				model.setValues(1, 1, 1, itemId, "CH", "ISIL-123", null);
				byte[] data = model.getBlock(48);
				model.setBlock(data, 4);
				String marker = getTagInfo();
				addInventoryItem(mockUid, model, "not found!!!", marker, "NXP Semiconductors (Test)", "ISO 15693 : NXP I-Code SLIX (Test)", itemList.size() + 1, itemList.size() + 1);
			} catch (Exception e) {
				print("[Test-Scan Fehler] " + e.getMessage() + "\n", itemList.size(), itemList.size());
			}
		}
	}

	// --- InventoryView Implementation ---

	@Override
	public void print(String text, int c1, int c2) {
		SwingUtilities.invokeLater(() -> {
			if (logArea != null) {
				logArea.append(text);
				if (logArea.getLineCount() > 500) {
					try {
						int end = logArea.getLineEndOffset(100);
						logArea.replaceRange("", 0, end);
					} catch (Exception ignored) {}
				}
				logArea.setCaretPosition(logArea.getDocument().getLength());
			}
			lblCountUniqueVal.setText(String.valueOf(c1));
			lblCountTotalVal.setText(String.valueOf(c2));
		});
	}

	@Override
	public String getTagInfo() {
		if (tInventoryTag != null) {
			return tInventoryTag.getText().trim();
		}
		return "";
	}

	@Override
	public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2) {
		addInventoryItem(uid, metadata, signature, marker, manufacturer, tagName, c1, c2, true, "OK");
	}

	@Override
	public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2, boolean syncOk, String statusDetails) {
		SwingUtilities.invokeLater(() -> {
			InventoryItemEntry item = new InventoryItemEntry();
			item.statusOk = syncOk;
			item.statusSymbol = syncOk ? "\u2714" : "\u2716";
			item.statusDetails = statusDetails != null ? statusDetails : (syncOk ? "OK" : "Fehler");
			item.index = c1;
			item.time = LocalDateTime.now().format(timeFormatter);
			item.uid = uid != null ? uid : "-";
			item.signature = signature != null ? signature : "-";
			item.marker = (marker != null && !marker.isEmpty()) ? marker : "-";
			item.manufacturer = manufacturer != null ? manufacturer : "-";
			item.tagName = tagName != null ? tagName : "-";

			if (metadata != null && !metadata.isEmpty()) {
				item.primaryItemId = metadata.getPrimaryItemId();
				item.partNumber = metadata.getPartNumber();
				item.partsInItem = metadata.getPartsInItem();
				item.partInfo = item.partNumber + " / " + item.partsInItem;
				item.country = metadata.getCountryOfOwnerLib();
				item.isil = metadata.getISIL();
				item.isilAndCountry = (item.country != null ? item.country + "-" : "") + (item.isil != null ? item.isil : "");
				item.usageType = metadata.getTypeOfUsage();
				item.crcStatus = metadata.getCRCError() ? "FEHLER" : "OK";
				item.version = metadata.getVersion();
				item.rawData = metadata.getData();
			} else {
				item.primaryItemId = "- (Leer/Unformatiert)";
				item.partInfo = "-";
				item.isilAndCountry = "-";
				item.usageType = 0;
				item.crcStatus = "-";
			}

			itemList.add(item);

			// Add to table model
			tableModel.addRow(new Object[] {
				item.statusSymbol,
				item.index,
				item.time,
				item.primaryItemId,
				item.signature,
				item.partInfo,
				item.isilAndCountry,
				item.usageType,
				item.marker,
				item.uid,
				item.crcStatus
			});

			// Scroll to bottom
			int lastRow = tableModel.getRowCount() - 1;
			if (lastRow >= 0) {
				int viewRow = table.convertRowIndexToView(lastRow);
				if (viewRow >= 0) {
					table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
				}
			}

			// Update KPI Banner
			lblCountUniqueVal.setText(String.valueOf(c1));
			lblCountTotalVal.setText(String.valueOf(c2));
			lblLastScannedVal.setText(item.primaryItemId + " (" + item.signature + ")");
			if (!"-".equals(item.marker)) {
				lblCurrentMarkerVal.setText(item.marker);
			}

			lblStatusBar.setText("Zuletzt erfasst: " + item.primaryItemId + " [UID: " + item.uid + "] um " + item.time);
			flashFeedback(item.statusOk && !"FEHLER".equalsIgnoreCase(item.crcStatus));
			updateInspector(item);
		});
	}

	@Override
	public void onReaderConnectionChanged(boolean connected, String deviceInfo) {
		updateReaderStatus(connected, deviceInfo);
		String info = (deviceInfo != null && !deviceInfo.isEmpty()) ? deviceInfo : "FEIG USB";
		print(connected ? ("[RFID-Reader] Verbunden: " + info + "\n") : "[RFID-Reader Fehler] Verbindung zum Reader getrennt!\n", itemList.size(), itemList.size());
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public boolean isDisposed() {
		return isDisposed;
	}

	@Override
	public void setCallback(InventoryCallback callback) {
		this.callback = callback;
	}

	@Override
	public void setThread(InventoryThread thread) {
		this.thread = thread;
	}

	private void handleExit() {
		int res = JOptionPane.showConfirmDialog(this,
				"M\u00F6chten Sie die RFID-Inventuranwendung wirklich beenden?",
				"Anwendung beenden",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (res == JOptionPane.YES_OPTION) {
			isDisposed = true;
			if (thread != null) {
				thread.dispose();
			}
			if (reader != null && reader.isConnected()) {
				try {
					reader.close();
				} catch (Exception ignored) {}
			}
			dispose();
			System.exit(0);
		}
	}
}

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
 * 
 * Diese Datei ist Teil von RFID Library Tools.
 *  
 * RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.library.inventory;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;
import org.objectspace.rfid.FinnishDataModel;

/**
 * Modernized, responsive user interface for RFID library inventory.
 * Features live data table, KPI metrics banner, live search, tag inspector, and CSV export.
 */
public class InventoryDialog extends Composite implements InventoryView {

	// Data model for inventory items (backwards compatibility alias)
	public static class InventoryItemEntry extends org.objectspace.rfid.library.inventory.InventoryItemEntry {
	}

	// UI Controls
	public Text tInventoryTag;
	private Button bStartStop;
	private Button btnTestScan;
	private Button btnClear;
	private Button btnExport;
	private Text txtSearch;
	private Table table;
	private StyledText logText;
	private Label lblReaderBadge;
	private Label lblStatusBadge;
	private Label lblCountUnique;
	private Label lblCountTotal;
	private Label lblLastScanned;
	private Label lblCurrentMarker;
	private Label lblStatusBar;

	// Inspector Details
	private Text detSyncStatus;
	private Text detBarcode;
	private Text detSignature;
	private Text detUID;
	private Text detManufacturer;
	private Text detModel;
	private Text detPartInfo;
	private Text detIsil;
	private Text detCountry;
	private Text detUsage;
	private Text detCRC;
	private Text detMarker;
	private StyledText detHexDump;

	// State
	protected Image logo = null;
	protected Image bgImage = null;
	protected boolean isRunning = false;
	protected boolean isReaderConnected = false;
	protected InventoryThread thread = null;
	protected InventoryCallback callback = null;
	private final List<InventoryItemEntry> itemList = new ArrayList<>();
	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

	public InventoryDialog(Composite parent, int style, Image logo, Image bgImage) {
		super(parent, style);
		this.logo = logo;
		this.bgImage = bgImage;

		setBackground(SWTResourceManager.getColor(245, 247, 250));
		GridLayout mainLayout = new GridLayout(1, false);
		mainLayout.marginWidth = 0;
		mainLayout.marginHeight = 0;
		mainLayout.verticalSpacing = 0;
		setLayout(mainLayout);

		createHeader();
		createMetricsBanner();
		createToolbar();
		createMainContent();
		createStatusBar();
		setupKeyboardShortcuts();

		updateStatusBadge(false);
	}

	/**
	 * Header bar with logo, title, and status pill.
	 */
	private void createHeader() {
		Composite header = new Composite(this, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		header.setLayoutData(gd);
		header.setBackground(SWTResourceManager.getColor(26, 36, 56)); // Dark slate blue

		GridLayout hl = new GridLayout(3, false);
		hl.marginWidth = 16;
		hl.marginHeight = 12;
		hl.horizontalSpacing = 16;
		header.setLayout(hl);

		// Logo Canvas (if available)
		if (logo != null) {
			Canvas cvsLogo = new Canvas(header, SWT.NONE);
			cvsLogo.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
			GridData lgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
			lgd.widthHint = Math.min(logo.getBounds().width, 180);
			lgd.heightHint = Math.min(logo.getBounds().height, 46);
			cvsLogo.setLayoutData(lgd);
			cvsLogo.addPaintListener(e -> {
				if (logo != null && !logo.isDisposed()) {
					e.gc.drawImage(logo, 0, 0);
				}
			});
		}

		// Titles
		Composite titleComp = new Composite(header, SWT.NONE);
		titleComp.setBackground(SWTResourceManager.getColor(26, 36, 56));
		titleComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout tl = new GridLayout(1, false);
		tl.marginWidth = 0;
		tl.marginHeight = 0;
		tl.verticalSpacing = 2;
		titleComp.setLayout(tl);

		Label lblTitle = new Label(titleComp, SWT.NONE);
		lblTitle.setText("RFID Inventory - info-age GmbH, Basel");
		lblTitle.setFont(SWTResourceManager.getFont("Segoe UI", 14, SWT.BOLD));
		lblTitle.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblTitle.setBackground(SWTResourceManager.getColor(26, 36, 56));

		Label lblSubtitle = new Label(titleComp, SWT.NONE);
		lblSubtitle.setText("RFID Inventarisierung & Medienpr\u00FCfung \u2013 ISO 28560 / FEIG SDK Gen3 (v7.1.0)");
		lblSubtitle.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		lblSubtitle.setForeground(SWTResourceManager.getColor(148, 163, 184));
		lblSubtitle.setBackground(SWTResourceManager.getColor(26, 36, 56));

		// Badges Container (Hardware Reader Status + Scan Workflow Status)
		Composite badgeComp = new Composite(header, SWT.NONE);
		badgeComp.setBackground(SWTResourceManager.getColor(26, 36, 56));
		GridData bgd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		badgeComp.setLayoutData(bgd);
		GridLayout bgl = new GridLayout(2, false);
		bgl.marginWidth = 0;
		bgl.marginHeight = 0;
		bgl.horizontalSpacing = 8;
		badgeComp.setLayout(bgl);

		// Hardware Reader Badge (FEIG)
		lblReaderBadge = new Label(badgeComp, SWT.CENTER);
		lblReaderBadge.setText("  \u25CB FEIG: Nicht verbunden  ");
		lblReaderBadge.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.BOLD));
		lblReaderBadge.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblReaderBadge.setBackground(SWTResourceManager.getColor(185, 28, 28)); // Red
		lblReaderBadge.setToolTipText("Kein FEIG Leseger\u00E4t angeschlossen. Bitte USB-Kabel verbinden.");
		GridData rgd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		rgd.heightHint = 28;
		lblReaderBadge.setLayoutData(rgd);

		// Live Scan Workflow Status Badge
		lblStatusBadge = new Label(badgeComp, SWT.CENTER);
		lblStatusBadge.setText("  \u23F8 BEREIT (PAUSIERT)  ");
		lblStatusBadge.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.BOLD));
		lblStatusBadge.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblStatusBadge.setBackground(SWTResourceManager.getColor(71, 85, 105)); // Slate 600
		GridData sgd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		sgd.heightHint = 28;
		lblStatusBadge.setLayoutData(sgd);
	}

	/**
	 * KPI Metrics Ribbon for real-time overview.
	 */
	private void createMetricsBanner() {
		Composite metricsComp = new Composite(this, SWT.NONE);
		metricsComp.setBackground(SWTResourceManager.getColor(245, 247, 250));
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		gd.horizontalIndent = 12;
		gd.verticalIndent = 10;
		gd.widthHint = SWT.DEFAULT;
		metricsComp.setLayoutData(gd);

		GridLayout gl = new GridLayout(4, true);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		gl.horizontalSpacing = 12;
		metricsComp.setLayout(gl);

		lblCountUnique = createMetricCard(metricsComp, "Erfasste Medien", "0", "Eindeutige Transponder", 37, 99, 235);
		lblCountTotal = createMetricCard(metricsComp, "Lesezyklen (Scans)", "0", "Gesamte RFID-Abfragen", 100, 116, 139);
		lblLastScanned = createMetricCard(metricsComp, "Zuletzt gescannt", "-", "Barcode / Signatur", 5, 150, 105);
		lblCurrentMarker = createMetricCard(metricsComp, "Aktiver Standort", "-", "Standort-Marker", 124, 58, 237);
	}

	private Label createMetricCard(Composite parent, String title, String initialVal, String subtext, int r, int g, int b) {
		Composite card = new Composite(parent, SWT.BORDER);
		card.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		card.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		GridLayout cl = new GridLayout(1, false);
		cl.marginWidth = 12;
		cl.marginHeight = 8;
		cl.verticalSpacing = 2;
		card.setLayout(cl);

		Label lblHeader = new Label(card, SWT.NONE);
		lblHeader.setText(title.toUpperCase());
		lblHeader.setFont(SWTResourceManager.getFont("Segoe UI", 8, SWT.BOLD));
		lblHeader.setForeground(SWTResourceManager.getColor(100, 116, 139));
		lblHeader.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		Label lblValue = new Label(card, SWT.NONE);
		lblValue.setText(initialVal);
		lblValue.setFont(SWTResourceManager.getFont("Segoe UI", 15, SWT.BOLD));
		lblValue.setForeground(SWTResourceManager.getColor(r, g, b));
		lblValue.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label lblFooter = new Label(card, SWT.NONE);
		lblFooter.setText(subtext);
		lblFooter.setFont(SWTResourceManager.getFont("Segoe UI", 8, SWT.NORMAL));
		lblFooter.setForeground(SWTResourceManager.getColor(148, 163, 184));
		lblFooter.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		return lblValue;
	}

	/**
	 * Toolbar with location tag input, Start/Stop toggle, Clear, Export, and Filter.
	 */
	private void createToolbar() {
		Composite toolbar = new Composite(this, SWT.NONE);
		toolbar.setBackground(SWTResourceManager.getColor(245, 247, 250));
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		gd.horizontalIndent = 12;
		gd.verticalIndent = 8;
		toolbar.setLayoutData(gd);

		GridLayout tl = new GridLayout(7, false);
		tl.marginWidth = 0;
		tl.marginHeight = 0;
		tl.horizontalSpacing = 8;
		toolbar.setLayout(tl);

		// Marker Label & Field
		Label lblMarker = new Label(toolbar, SWT.NONE);
		lblMarker.setText("Standort-Marker:");
		lblMarker.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.BOLD));
		lblMarker.setBackground(SWTResourceManager.getColor(245, 247, 250));

		tInventoryTag = new Text(toolbar, SWT.BORDER);
		tInventoryTag.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.NORMAL));
		tInventoryTag.setMessage("z. B. Regal 12 / Fach B");
		GridData tgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		tgd.widthHint = 160;
		tgd.heightHint = 22;
		tInventoryTag.setLayoutData(tgd);
		tInventoryTag.addModifyListener(e -> {
			String m = tInventoryTag.getText().trim();
			lblCurrentMarker.setText(m.isEmpty() ? "-" : m);
		});

		// Start / Stop Button
		bStartStop = new Button(toolbar, SWT.PUSH);
		bStartStop.setText("\u25B6  Scan starten");
		bStartStop.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.BOLD));
		bStartStop.setEnabled(false);
		bStartStop.setBackground(SWTResourceManager.getColor(156, 163, 175)); // Inactive Gray
		bStartStop.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		bStartStop.setToolTipText("Inventarisierung starten / anhalten [F5]. Kein Leseger\u00E4t verbunden.");
		GridData bgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		bgd.widthHint = 125;
		bgd.heightHint = 32;
		bStartStop.setLayoutData(bgd);
		bStartStop.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleScan();
			}
		});

		// Test Scan Button (analogous to Android App)
		btnTestScan = new Button(toolbar, SWT.PUSH);
		btnTestScan.setText("Test-Scan");
		btnTestScan.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		btnTestScan.setToolTipText("Simulierten RFID-Scan einf\u00FCgen [Strg+T] (Test-Eintrag)");
		GridData tbgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		tbgd.widthHint = 90;
		tbgd.heightHint = 32;
		btnTestScan.setLayoutData(tbgd);
		btnTestScan.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				triggerTestScan();
			}
		});

		// Clear Button
		btnClear = new Button(toolbar, SWT.PUSH);
		btnClear.setText("Leeren");
		btnClear.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		btnClear.setToolTipText("Alle Eintr\u00E4ge aus der Tabelle l\u00F6schen [Strg+L]");
		GridData cgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		cgd.widthHint = 80;
		cgd.heightHint = 32;
		btnClear.setLayoutData(cgd);
		btnClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clearItems();
			}
		});

		// Export CSV Button
		btnExport = new Button(toolbar, SWT.PUSH);
		btnExport.setText("CSV Export");
		btnExport.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		btnExport.setToolTipText("Erfasste Medien in eine UTF-8 CSV-Datei exportieren [Strg+E]");
		GridData egd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		egd.widthHint = 95;
		egd.heightHint = 32;
		btnExport.setLayoutData(egd);
		btnExport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				exportToCSV();
			}
		});

		// Search / Filter
		txtSearch = new Text(toolbar, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH);
		txtSearch.setMessage("Suchen (Strg+F)...");
		txtSearch.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		txtSearch.setToolTipText("Tabelle filtern nach Barcode, Signatur, UID [Strg+F, Esc zum Zur\u00FCcksetzen]");
		GridData sgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		sgd.minimumWidth = 130;
		sgd.heightHint = 22;
		txtSearch.setLayoutData(sgd);
		txtSearch.addModifyListener(e -> filterTable(txtSearch.getText().trim()));
	}

	/**
	 * Main tabbed content: Inventory Table, Tag Details Inspector, System Log.
	 */
	private void createMainContent() {
		CTabFolder tabFolder = new CTabFolder(this, SWT.BORDER | SWT.FLAT);
		tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tabFolder.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.NORMAL));
		tabFolder.setSelectionBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		tabFolder.setSimple(false);

		// Tab 1: Live Inventory Table
		CTabItem tabTable = new CTabItem(tabFolder, SWT.NONE);
		tabTable.setText("  Inventarliste  ");
		tabTable.setControl(createTableComposite(tabFolder));

		// Tab 2: Tag Details Inspector
		CTabItem tabDetails = new CTabItem(tabFolder, SWT.NONE);
		tabDetails.setText("  Tag-Inspektor & Details  ");
		tabDetails.setControl(createInspectorComposite(tabFolder));

		// Tab 3: System & Raw Scan Protocol
		CTabItem tabLog = new CTabItem(tabFolder, SWT.NONE);
		tabLog.setText("  Scan-Protokoll  ");
		tabLog.setControl(createLogComposite(tabFolder));

		tabFolder.setSelection(0);
	}

	private Composite createTableComposite(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		comp.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		table = new Table(comp, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		String[] colTitles = {
			"Status", "#", "Uhrzeit", "Barcode / Medien-ID", "Signatur", "Teil", "ISIL / Land", "Nutzung", "Standort-Marker", "UID", "CRC-Pr\u00FCfung"
		};
		int[] colWidths = { 50, 40, 75, 140, 140, 50, 95, 65, 110, 150, 85 };
		int[] colAligns = { SWT.CENTER, SWT.CENTER, SWT.CENTER, SWT.LEFT, SWT.LEFT, SWT.CENTER, SWT.LEFT, SWT.CENTER, SWT.LEFT, SWT.LEFT, SWT.CENTER };

		for (int i = 0; i < colTitles.length; i++) {
			TableColumn col = new TableColumn(table, colAligns[i]);
			col.setText(colTitles[i]);
			col.setWidth(colWidths[i]);
		}

		table.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = table.getSelectionIndex();
				if (idx >= 0 && idx < itemList.size()) {
					updateInspector(itemList.get(idx));
				}
			}
		});

		return comp;
	}

	private Composite createInspectorComposite(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setBackground(SWTResourceManager.getColor(245, 247, 250));
		GridLayout gl = new GridLayout(2, false);
		gl.marginWidth = 16;
		gl.marginHeight = 16;
		gl.horizontalSpacing = 16;
		comp.setLayout(gl);

		// Left Card: Metadata Fields
		Group grpMeta = new Group(comp, SWT.NONE);
		grpMeta.setText(" ISO 28560 Transponderdaten ");
		grpMeta.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.BOLD));
		grpMeta.setBackground(SWTResourceManager.getColor(245, 247, 250));
		grpMeta.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout ml = new GridLayout(2, false);
		ml.marginWidth = 12;
		ml.marginHeight = 12;
		ml.verticalSpacing = 6;
		grpMeta.setLayout(ml);

		detSyncStatus = addInspectorRow(grpMeta, "\u00DCbertragung (DB/Webservice):");
		detBarcode = addInspectorRow(grpMeta, "Barcode / Primary Item ID:");
		detSignature = addInspectorRow(grpMeta, "Katalog-Signatur:");
		detUID = addInspectorRow(grpMeta, "RFID Chip UID:");
		detManufacturer = addInspectorRow(grpMeta, "Chip-Hersteller:");
		detModel = addInspectorRow(grpMeta, "Transponder-Typ:");
		detPartInfo = addInspectorRow(grpMeta, "Teil-Information:");
		detIsil = addInspectorRow(grpMeta, "ISIL Kennung:");
		detCountry = addInspectorRow(grpMeta, "L\u00E4ndercode:");
		detUsage = addInspectorRow(grpMeta, "Nutzungsart (Usage Type):");
		detCRC = addInspectorRow(grpMeta, "CRC Pr\u00FCfsummen-Status:");
		detMarker = addInspectorRow(grpMeta, "Standort-Marker:");

		// Right Card: Hexdump and Raw Data
		Group grpHex = new Group(comp, SWT.NONE);
		grpHex.setText(" Block-Rohdaten (Hex-Dump) ");
		grpHex.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.BOLD));
		grpHex.setBackground(SWTResourceManager.getColor(245, 247, 250));
		grpHex.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		grpHex.setLayout(new GridLayout(1, false));

		detHexDump = new StyledText(grpHex, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		detHexDump.setFont(SWTResourceManager.getFont("Consolas", 10, SWT.NORMAL));
		detHexDump.setEditable(false);
		detHexDump.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		detHexDump.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		return comp;
	}

	private Text addInspectorRow(Composite parent, String labelText) {
		Label lbl = new Label(parent, SWT.NONE);
		lbl.setText(labelText);
		lbl.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.BOLD));
		lbl.setBackground(SWTResourceManager.getColor(245, 247, 250));
		lbl.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		Text txt = new Text(parent, SWT.BORDER | SWT.READ_ONLY);
		txt.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		txt.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.heightHint = 20;
		txt.setLayoutData(gd);
		return txt;
	}

	private Composite createLogComposite(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		comp.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		logText = new StyledText(comp, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		logText.setForeground(SWTResourceManager.getColor(30, 41, 59));
		logText.setBackground(SWTResourceManager.getColor(250, 250, 250));
		logText.setFont(SWTResourceManager.getFont("Consolas", 9, SWT.NORMAL));
		logText.setEditable(false);
		logText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		logText.addModifyListener(e -> {
			if (!logText.isDisposed()) {
				logText.setTopIndex(logText.getLineCount() - 1);
			}
		});

		return comp;
	}

	private void createStatusBar() {
		Composite status = new Composite(this, SWT.NONE);
		status.setBackground(SWTResourceManager.getColor(226, 232, 240)); // Slate 200
		GridData gd = new GridData(SWT.FILL, SWT.BOTTOM, true, false);
		status.setLayoutData(gd);

		GridLayout sl = new GridLayout(1, false);
		sl.marginWidth = 12;
		sl.marginHeight = 4;
		status.setLayout(sl);

		lblStatusBar = new Label(status, SWT.NONE);
		lblStatusBar.setText("FEIG Leseger\u00E4t nicht verbunden. Bitte USB-Kabel des Leseger\u00E4ts anschlie\u00DFen.");
		lblStatusBar.setFont(SWTResourceManager.getFont("Segoe UI", 8, SWT.NORMAL));
		lblStatusBar.setForeground(SWTResourceManager.getColor(71, 85, 105));
		lblStatusBar.setBackground(SWTResourceManager.getColor(226, 232, 240));
		lblStatusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	public void toggleScan() {
		if (!isReaderConnected) {
			return;
		}
		if (!isRunning) {
			isRunning = true;
			bStartStop.setText("\u25A0  Scan anhalten");
			bStartStop.setBackground(SWTResourceManager.getColor(220, 38, 38)); // Red
			bStartStop.setToolTipText("Inventarisierung pausieren [F5]");
			updateStatusBadge(true);
			lblStatusBar.setText("RFID-Scanner aktiv. Suche nach Transpondern im Antennenfeld...");
			if (thread != null) {
				thread.pause(false);
			}
		} else {
			isRunning = false;
			bStartStop.setText("\u25B6  Scan starten");
			bStartStop.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Green
			bStartStop.setToolTipText("Inventarisierung starten [F5]");
			updateStatusBadge(false);
			lblStatusBar.setText("Scan pausiert. Bisher " + itemList.size() + " Medien erfasst.");
			if (thread != null) {
				thread.pause(true);
			}
		}
	}

	private void updateStatusBadge(boolean active) {
		if (lblStatusBadge == null || lblStatusBadge.isDisposed()) return;
		if (active) {
			lblStatusBadge.setText("  \u25CF SCAN AKTIV  ");
			lblStatusBadge.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Green
		} else {
			lblStatusBadge.setText("  \u23F8 BEREIT (PAUSIERT)  ");
			lblStatusBadge.setBackground(SWTResourceManager.getColor(71, 85, 105)); // Slate
		}
	}

	private void flashScanFeedback(boolean success) {
		if (lblStatusBadge == null || lblStatusBadge.isDisposed()) return;
		org.eclipse.swt.graphics.Color originalColor = isRunning ?
				SWTResourceManager.getColor(5, 150, 105) : SWTResourceManager.getColor(71, 85, 105);
		org.eclipse.swt.graphics.Color flashColor = success ?
				SWTResourceManager.getColor(37, 99, 235) : SWTResourceManager.getColor(220, 38, 38);

		lblStatusBadge.setBackground(flashColor);
		getDisplay().timerExec(160, () -> {
			if (!lblStatusBadge.isDisposed()) {
				lblStatusBadge.setBackground(originalColor);
			}
		});
	}

	/**
	 * Configures global and keyboard shortcut filters for the application.
	 * Supports:
	 * - F5: Start / Stop Scan toggle
	 * - Ctrl+F: Focus search field and select text
	 * - Ctrl+E: Export to CSV
	 * - Ctrl+T: Simulated RFID test scan
	 * - Ctrl+L: Clear table list
	 * - Esc: Clear search and return focus to table
	 */
	private void setupKeyboardShortcuts() {
		Display display = getDisplay();
		if (display == null) return;

		display.addFilter(SWT.KeyDown, event -> {
			if (isDisposed()) return;

			// F5: Start / Stop Scan
			if (event.keyCode == SWT.F5) {
				event.doit = false;
				toggleScan();
				return;
			}

			// Ctrl + F: Focus Search
			if ((event.stateMask & SWT.MOD1) != 0 && (event.keyCode == 'f' || event.keyCode == 'F')) {
				if (txtSearch != null && !txtSearch.isDisposed()) {
					event.doit = false;
					txtSearch.setFocus();
					txtSearch.selectAll();
				}
				return;
			}

			// Ctrl + E: CSV Export
			if ((event.stateMask & SWT.MOD1) != 0 && (event.keyCode == 'e' || event.keyCode == 'E')) {
				event.doit = false;
				exportToCSV();
				return;
			}

			// Ctrl + T: Test Scan
			if ((event.stateMask & SWT.MOD1) != 0 && (event.keyCode == 't' || event.keyCode == 'T')) {
				event.doit = false;
				triggerTestScan();
				return;
			}

			// Ctrl + L: Clear items
			if ((event.stateMask & SWT.MOD1) != 0 && (event.keyCode == 'l' || event.keyCode == 'L')) {
				event.doit = false;
				clearItems();
				return;
			}

			// Esc: Clear search
			if (event.keyCode == SWT.ESC) {
				if (txtSearch != null && !txtSearch.isDisposed() && !txtSearch.getText().isEmpty()) {
					event.doit = false;
					txtSearch.setText("");
					if (table != null && !table.isDisposed()) {
						table.setFocus();
					}
				}
			}
		});
	}

	/**
	 * Callback from background thread when the hardware connection state changes.
	 * Updates the reader badge, start/stop button enablement, status bar text, and log dynamically.
	 * 
	 * @param connected true if FEIG device is plugged in and ready
	 * @param deviceInfo hardware details (e.g. device ID / model)
	 */
	public void onReaderConnectionChanged(boolean connected, String deviceInfo) {
		if (isDisposed()) return;
		getDisplay().asyncExec(() -> {
			if (isDisposed()) return;
			this.isReaderConnected = connected;
			if (connected) {
				if (lblReaderBadge != null && !lblReaderBadge.isDisposed()) {
					lblReaderBadge.setText("  \u25CF FEIG: Verbunden  ");
					lblReaderBadge.setToolTipText(deviceInfo != null ? deviceInfo : "FEIG USB Reader verbunden");
					lblReaderBadge.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Emerald Green
				}
				if (bStartStop != null && !bStartStop.isDisposed()) {
					bStartStop.setEnabled(true);
					if (isRunning) {
						bStartStop.setText("\u25A0  Scan anhalten");
						bStartStop.setBackground(SWTResourceManager.getColor(220, 38, 38)); // Red
						bStartStop.setToolTipText("Inventarisierung pausieren [F5]");
					} else {
						bStartStop.setText("\u25B6  Scan starten");
						bStartStop.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Green
						bStartStop.setToolTipText("Inventarisierung starten [F5]");
					}
				}
				if (lblStatusBar != null && !lblStatusBar.isDisposed()) {
					if (!isRunning) {
						lblStatusBar.setText("FEIG Leseger\u00E4t verbunden (" + (deviceInfo != null ? deviceInfo : "USB") + "). Bereit zum Scannen.");
					}
				}
				addLogMessage("FEIG Leseger\u00E4t verbunden (" + (deviceInfo != null ? deviceInfo : "USB-Ger\u00E4t erkannt") + ")");
			} else {
				if (lblReaderBadge != null && !lblReaderBadge.isDisposed()) {
					lblReaderBadge.setText("  \u25CB FEIG: Getrennt  ");
					lblReaderBadge.setToolTipText("Kein FEIG Leseger\u00E4t angeschlossen. Bitte USB-Kabel verbinden.");
					lblReaderBadge.setBackground(SWTResourceManager.getColor(185, 28, 28)); // Vivid Red
				}
				if (isRunning) {
					isRunning = false;
					updateStatusBadge(false);
					if (thread != null) {
						thread.pause(true);
					}
				}
				if (bStartStop != null && !bStartStop.isDisposed()) {
					bStartStop.setEnabled(false);
					bStartStop.setText("\u25B6  Scan starten");
					bStartStop.setBackground(SWTResourceManager.getColor(156, 163, 175)); // Inactive Gray
					bStartStop.setToolTipText("Inventarisierung starten [F5]. Kein Leseger\u00E4t verbunden.");
				}
				if (lblStatusBar != null && !lblStatusBar.isDisposed()) {
					lblStatusBar.setText("FEIG Leseger\u00E4t nicht verbunden. Bitte USB-Kabel anschlie\u00DFen.");
				}
				addLogMessage("WARNUNG: FEIG Leseger\u00E4t getrennt oder nicht erreichbar. Scan deaktiviert.");
			}
			if (lblReaderBadge != null && lblReaderBadge.getParent() != null && !lblReaderBadge.getParent().isDisposed()) {
				lblReaderBadge.getParent().layout(true, true);
			}
		});
	}

	/**
	 * Appends a timestamped system log line to the scan protocol tab.
	 * 
	 * @param message log text
	 */
	public void addLogMessage(String message) {
		if (isDisposed()) return;
		getDisplay().asyncExec(() -> {
			if (isDisposed() || logText == null || logText.isDisposed()) return;
			String time = LocalDateTime.now().format(timeFormatter);
			logText.append("[" + time + "] " + message + "\n");
			logText.setTopIndex(logText.getLineCount() - 1);
		});
	}

	/**
	 * Adds a structured RFID tag entry to the table and updates all UI metrics.
	 */
	public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2) {
		addInventoryItem(uid, metadata, signature, marker, manufacturer, tagName, c1, c2, true, "OK");
	}

	/**
	 * Adds a structured RFID tag entry with execution/sync status to the table and updates all UI metrics.
	 */
	public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2, boolean syncOk, String statusDetails) {
		if (isDisposed()) return;

		getDisplay().asyncExec(() -> {
			if (isDisposed()) return;

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

			// Add row to Table
			TableItem ti = new TableItem(table, SWT.NONE);
			ti.setText(new String[] {
				item.statusSymbol,
				String.valueOf(item.index),
				item.time,
				item.primaryItemId,
				item.signature,
				item.partInfo,
				item.isilAndCountry,
				String.valueOf(item.usageType),
				item.marker,
				item.uid,
				item.crcStatus
			});

			if (item.statusOk) {
				ti.setForeground(0, SWTResourceManager.getColor(22, 163, 74)); // Green checkmark
			} else {
				ti.setForeground(0, SWTResourceManager.getColor(220, 38, 38)); // Red error symbol
				ti.setBackground(SWTResourceManager.getColor(254, 226, 226)); // Soft red row background
			}

			if ("FEHLER".equals(item.crcStatus)) {
				ti.setBackground(SWTResourceManager.getColor(254, 226, 226)); // Soft red
			}

			// Scroll to last item
			table.showItem(ti);

			// Update KPI Banner
			lblCountUnique.setText(String.valueOf(c1));
			lblCountTotal.setText(String.valueOf(c2));
			lblLastScanned.setText(item.primaryItemId + " (" + item.signature + ")");
			if (!item.marker.equals("-")) {
				lblCurrentMarker.setText(item.marker);
			}

			lblStatusBar.setText("Zuletzt erfasst: " + item.primaryItemId + " [UID: " + item.uid + "] um " + item.time);

			// Trigger visual scan feedback
			boolean isSuccess = item.statusOk && !"FEHLER".equals(item.crcStatus);
			flashScanFeedback(isSuccess);

			// Update Inspector
			updateInspector(item);
		});
	}

	private void updateInspector(InventoryItemEntry item) {
		if (detBarcode == null || detBarcode.isDisposed()) return;

		if (detSyncStatus != null && !detSyncStatus.isDisposed()) {
			detSyncStatus.setText(item.statusDetails != null ? item.statusDetails : (item.statusOk ? "OK (\u2714)" : "Fehler (\u2716)"));
		}
		detBarcode.setText(item.primaryItemId != null ? item.primaryItemId : "-");
		detSignature.setText(item.signature != null ? item.signature : "-");
		detUID.setText(item.uid != null ? item.uid : "-");
		detManufacturer.setText(item.manufacturer != null ? item.manufacturer : "-");
		detModel.setText(item.tagName != null ? item.tagName : "-");
		detPartInfo.setText(item.partInfo != null ? item.partInfo : "-");
		detIsil.setText(item.isil != null ? item.isil : "-");
		detCountry.setText(item.country != null ? item.country : "-");
		detUsage.setText(String.valueOf(item.usageType));
		detCRC.setText(item.crcStatus != null ? item.crcStatus : "-");
		detMarker.setText(item.marker != null ? item.marker : "-");

		// Format Hexdump
		if (item.rawData != null && item.rawData.length > 0) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < item.rawData.length; i += 4) {
				sb.append(String.format("Block %02d:  ", i / 4));
				for (int j = 0; j < 4; j++) {
					if (i + j < item.rawData.length) {
						sb.append(String.format("%02X ", item.rawData[i + j]));
					} else {
						sb.append("   ");
					}
				}
				sb.append("  |");
				for (int j = 0; j < 4 && (i + j) < item.rawData.length; j++) {
					byte b = item.rawData[i + j];
					sb.append((b >= 32 && b <= 126) ? (char) b : '.');
				}
				sb.append("|\n");
			}
			detHexDump.setText(sb.toString());
		} else {
			detHexDump.setText("Keine Rohdaten verf\u00FCgbar.");
		}
	}

	/**
	 * Filters table rows based on query.
	 */
	private void filterTable(String query) {
		table.removeAll();
		String lower = query.toLowerCase();

		for (InventoryItemEntry item : itemList) {
			if (lower.isEmpty() ||
				item.primaryItemId.toLowerCase().contains(lower) ||
				item.signature.toLowerCase().contains(lower) ||
				item.uid.toLowerCase().contains(lower) ||
				item.marker.toLowerCase().contains(lower)) {

				TableItem ti = new TableItem(table, SWT.NONE);
				ti.setText(new String[] {
					item.statusSymbol,
					String.valueOf(item.index),
					item.time,
					item.primaryItemId,
					item.signature,
					item.partInfo,
					item.isilAndCountry,
					String.valueOf(item.usageType),
					item.marker,
					item.uid,
					item.crcStatus
				});
				if (item.statusOk) {
					ti.setForeground(0, SWTResourceManager.getColor(22, 163, 74));
				} else {
					ti.setForeground(0, SWTResourceManager.getColor(220, 38, 38));
					ti.setBackground(SWTResourceManager.getColor(254, 226, 226));
				}
				if ("FEHLER".equals(item.crcStatus)) {
					ti.setBackground(SWTResourceManager.getColor(254, 226, 226));
				}
			}
		}
	}

	/**
	 * Exports all scanned items to a clean UTF-8 CSV file.
	 */
	private void exportToCSV() {
		if (itemList.isEmpty()) {
			MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
			box.setText("CSV Export");
			box.setMessage("Keine Eintr\u00E4ge zum Exportieren vorhanden.");
			box.open();
			return;
		}

		FileDialog fd = new FileDialog(getShell(), SWT.SAVE);
		fd.setText("Inventarliste als CSV speichern");
		fd.setFilterExtensions(new String[] { "*.csv", "*.*" });
		fd.setFilterNames(new String[] { "CSV-Dateien (*.csv)", "Alle Dateien (*.*)" });
		fd.setFileName("Inventar_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");

		String path = fd.open();
		if (path != null) {
			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
				// UTF-8 BOM for Excel compatibility
				pw.print('\ufeff');
				pw.println("Status;Nr;Uhrzeit;Barcode_ID;Signatur;Teil_Nr;Teile_Gesamt;ISIL;Land;Nutzungsart;Standort_Marker;RFID_UID;CRC_Status;Hersteller;Transponder_Typ;Status_Details");
				for (InventoryItemEntry item : itemList) {
					pw.printf("\"%s\";%d;\"%s\";\"%s\";\"%s\";%d;%d;\"%s\";\"%s\";%d;\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"%n",
						item.statusSymbol,
						item.index,
						item.time,
						item.primaryItemId.replace("\"", "\"\""),
						item.signature.replace("\"", "\"\""),
						item.partNumber,
						item.partsInItem,
						item.isil != null ? item.isil : "",
						item.country != null ? item.country : "",
						item.usageType,
						item.marker.replace("\"", "\"\""),
						item.uid,
						item.crcStatus,
						item.manufacturer,
						item.tagName,
						item.statusDetails != null ? item.statusDetails.replace("\"", "\"\"") : ""
					);
				}
				MessageBox box = new MessageBox(getShell(), SWT.ICON_INFORMATION | SWT.OK);
				box.setText("CSV Export erfolgreich");
				box.setMessage("Die Inventarliste mit " + itemList.size() + " Datens\u00E4tzen wurde erfolgreich gespeichert:\n" + path);
				box.open();
			} catch (Exception ex) {
				MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
				box.setText("Fehler beim Exportieren");
				box.setMessage("Konnte CSV-Datei nicht schreiben: " + ex.getMessage());
				box.open();
			}
		}
	}

	public void clearItems() {
		itemList.clear();
		table.removeAll();
		lblCountUnique.setText("0");
		lblCountTotal.setText("0");
		lblLastScanned.setText("-");
		if (logText != null && !logText.isDisposed()) {
			logText.setText("");
		}
		if (detSyncStatus != null && !detSyncStatus.isDisposed()) {
			detSyncStatus.setText("");
		}
		if (detBarcode != null && !detBarcode.isDisposed()) {
			detBarcode.setText("");
			detSignature.setText("");
			detUID.setText("");
			detManufacturer.setText("");
			detModel.setText("");
			detPartInfo.setText("");
			detIsil.setText("");
			detCountry.setText("");
			detUsage.setText("");
			detCRC.setText("");
			detMarker.setText("");
			detHexDump.setText("");
		}
		lblStatusBar.setText("Inventarliste zur\u00FCckgesetzt.");
		InventoryCallback cb = getCallback();
		if (cb != null) {
			cb.clearUIDList();
		}
	}

	public void print(String t, int c1, int c2) {
		if (logText != null && !logText.isDisposed()) {
			logText.append(t);
			if (logText.getLineCount() > 500) {
				String txt = logText.getText();
				String[] lines = txt.split("\\r?\\n");
				StringBuilder sb = new StringBuilder();
				for (int i = lines.length - 400; i < lines.length; i++) {
					sb.append(lines[i]).append("\n");
				}
				logText.setText(sb.toString());
			}
		}
		lblCountUnique.setText(String.valueOf(c1));
		lblCountTotal.setText(String.valueOf(c2));
	}

	public void println(String t, int c1, int c2) {
		print(t + "\n", c1, c2);
	}

	public String getTagInfo() {
		if (tInventoryTag != null && !tInventoryTag.isDisposed()) {
			return tInventoryTag.getText().trim();
		}
		return "";
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setThread(InventoryThread thread) {
		this.thread = thread;
	}

	public void setCallback(InventoryCallback callback) {
		this.callback = callback;
	}

	public InventoryCallback getCallback() {
		if (callback != null) {
			return callback;
		}
		if (thread != null) {
			return thread.getInventoryCallback();
		}
		return null;
	}

	/**
	 * Triggers a mock/test RFID tag scan analogous to the Android NFC Reader application.
	 * Can be executed at any time, even when no physical FEIG reader is connected.
	 */
	public void triggerTestScan() {
		InventoryCallback cb = getCallback();
		if (cb != null) {
			cb.triggerTestScan();
		} else {
			// Fallback: directly create and display mock item in UI
			int randomSuffix = 1000 + (int) (Math.random() * 9000);
			String mockUid = String.format("E0040150%04dABCD", randomSuffix);
			String itemId = "3011" + randomSuffix;
			try {
				FinnishDataModel model = new FinnishDataModel();
				model.setValues(1, 1, 1, itemId, "CH", "ISIL-123", null);
				byte[] data = model.getBlock(48);
				model.setBlock(data, 4);
				String marker = (tInventoryTag != null && !tInventoryTag.getText().trim().isEmpty()) ? tInventoryTag.getText().trim() : "-";
				addInventoryItem(mockUid, model, "not found!!!", marker, "NXP Semiconductors (Test)", "ISO 15693 : NXP I-Code SLIX (Test)", itemList.size() + 1, itemList.size() + 1);
			} catch (Exception e) {
				addLogMessage("Fehler beim Erstellen des Test-Eintrags: " + e.getMessage());
			}
		}
	}
}

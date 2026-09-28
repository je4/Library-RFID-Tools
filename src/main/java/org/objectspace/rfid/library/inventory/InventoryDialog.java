/*******************************************************************************
 * Copyright 2015-2026
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/Goettingen
 *
 * This file is part of HAWK RFID Library Tools.
 * 
 * HAWK RFID Library Tools is free software: you can redistribute it and/or modify
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
public class InventoryDialog extends Composite {

	// Data model for inventory items
	public static class InventoryItemEntry {
		public int index;
		public String time;
		public String primaryItemId;
		public String signature;
		public String partInfo;
		public String isilAndCountry;
		public int usageType;
		public String marker;
		public String uid;
		public String crcStatus;
		public String manufacturer;
		public String tagName;
		public byte[] rawData;
		public int version;
		public int partNumber;
		public int partsInItem;
		public String country;
		public String isil;
	}

	// UI Controls
	public Text tInventoryTag;
	private Button bStartStop;
	private Button btnClear;
	private Button btnExport;
	private Text txtSearch;
	private Table table;
	private StyledText logText;
	private Label lblStatusBadge;
	private Label lblCountUnique;
	private Label lblCountTotal;
	private Label lblLastScanned;
	private Label lblCurrentMarker;
	private Label lblStatusBar;

	// Inspector Details
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
	protected InventoryThread thread = null;
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
		lblTitle.setText("RFID Inventarisierung & Medienpr\u00FCfung");
		lblTitle.setFont(SWTResourceManager.getFont("Segoe UI", 14, SWT.BOLD));
		lblTitle.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblTitle.setBackground(SWTResourceManager.getColor(26, 36, 56));

		Label lblSubtitle = new Label(titleComp, SWT.NONE);
		lblSubtitle.setText("ISO 28560 / Finnish Data Model - FEIG SDK v5.6.3");
		lblSubtitle.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		lblSubtitle.setForeground(SWTResourceManager.getColor(148, 163, 184));
		lblSubtitle.setBackground(SWTResourceManager.getColor(26, 36, 56));

		// Live Status Badge
		lblStatusBadge = new Label(header, SWT.CENTER);
		lblStatusBadge.setText("  \u25CF BEREIT (PAUSIERT)  ");
		lblStatusBadge.setFont(SWTResourceManager.getFont("Segoe UI", 10, SWT.BOLD));
		lblStatusBadge.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblStatusBadge.setBackground(SWTResourceManager.getColor(71, 85, 105)); // Slate 600
		GridData bgd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		bgd.heightHint = 28;
		lblStatusBadge.setLayoutData(bgd);
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

		GridLayout tl = new GridLayout(6, false);
		tl.marginWidth = 0;
		tl.marginHeight = 0;
		tl.horizontalSpacing = 10;
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
		tgd.widthHint = 220;
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
		bStartStop.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Green
		bStartStop.setForeground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		GridData bgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		bgd.widthHint = 140;
		bgd.heightHint = 32;
		bStartStop.setLayoutData(bgd);
		bStartStop.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				toggleScan();
			}
		});

		// Clear Button
		btnClear = new Button(toolbar, SWT.PUSH);
		btnClear.setText("Liste leeren");
		btnClear.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		GridData cgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		cgd.widthHint = 110;
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
		GridData egd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		egd.widthHint = 110;
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
		txtSearch.setMessage("Tabelle durchsuchen (Barcode, Signatur, UID)...");
		txtSearch.setFont(SWTResourceManager.getFont("Segoe UI", 9, SWT.NORMAL));
		GridData sgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
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
			"#", "Uhrzeit", "Barcode / Medien-ID", "Signatur", "Teil", "ISIL / Land", "Nutzung", "Standort-Marker", "UID", "CRC-Pr\u00FCfung"
		};
		int[] colWidths = { 45, 75, 170, 180, 65, 110, 80, 140, 180, 95 };
		int[] colAligns = { SWT.CENTER, SWT.CENTER, SWT.LEFT, SWT.LEFT, SWT.CENTER, SWT.LEFT, SWT.CENTER, SWT.LEFT, SWT.LEFT, SWT.CENTER };

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
		lblStatusBar.setText("Bereit. Klicken Sie auf 'Scan starten', um die automatische Inventarisierung zu beginnen.");
		lblStatusBar.setFont(SWTResourceManager.getFont("Segoe UI", 8, SWT.NORMAL));
		lblStatusBar.setForeground(SWTResourceManager.getColor(71, 85, 105));
		lblStatusBar.setBackground(SWTResourceManager.getColor(226, 232, 240));
		lblStatusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	public void toggleScan() {
		if (!isRunning) {
			isRunning = true;
			bStartStop.setText("\u25A0  Scan anhalten");
			bStartStop.setBackground(SWTResourceManager.getColor(220, 38, 38)); // Red
			updateStatusBadge(true);
			lblStatusBar.setText("RFID-Scanner aktiv. Suche nach Transpondern im Antennenfeld...");
			if (thread != null) {
				thread.pause(false);
			}
		} else {
			isRunning = false;
			bStartStop.setText("\u25B6  Scan starten");
			bStartStop.setBackground(SWTResourceManager.getColor(5, 150, 105)); // Green
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
			lblStatusBadge.setBackground(SWTResourceManager.getColor(5, 150, 105));
		} else {
			lblStatusBadge.setText("  \u23F8 BEREIT (PAUSIERT)  ");
			lblStatusBadge.setBackground(SWTResourceManager.getColor(71, 85, 105));
		}
	}

	/**
	 * Adds a structured RFID tag entry to the table and updates all UI metrics.
	 */
	public void addInventoryItem(String uid, FinnishDataModel metadata, String signature, String marker,
			String manufacturer, String tagName, int c1, int c2) {
		if (isDisposed()) return;

		getDisplay().asyncExec(() -> {
			if (isDisposed()) return;

			InventoryItemEntry item = new InventoryItemEntry();
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

			// Update Inspector
			updateInspector(item);
		});
	}

	private void updateInspector(InventoryItemEntry item) {
		if (detBarcode == null || detBarcode.isDisposed()) return;

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
				for (int j = 0; j < 4 && (i + j) < item.rawData.length; j++) {
					sb.append(String.format("%02X ", item.rawData[i + j]));
				}
				sb.append("   |");
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
				pw.println("Nr;Uhrzeit;Barcode_ID;Signatur;Teil_Nr;Teile_Gesamt;ISIL;Land;Nutzungsart;Standort_Marker;RFID_UID;CRC_Status;Hersteller;Transponder_Typ");
				for (InventoryItemEntry item : itemList) {
					pw.printf("%d;\"%s\";\"%s\";\"%s\";%d;%d;\"%s\";\"%s\";%d;\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"%n",
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
						item.tagName
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

	public boolean isRunning() {
		return isRunning;
	}

	public void setThread(InventoryThread thread) {
		this.thread = thread;
	}
}

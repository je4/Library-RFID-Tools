/*******************************************************************************
 * Copyright 2015
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
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
 * 
 * Diese Datei ist Teil von HAWK RFID Library Tools.
 *  
 * HAWK RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
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
package org.objectspace.rfid.library.taghandle;

import java.util.ArrayList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.wb.swt.SWTResourceManager;
import org.mihalis.opal.rangeSlider.RangeSlider;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.FinnishDataModelOptionalBlock;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;

public class MainDialog extends Composite {

	private final FormToolkit toolkit = new FormToolkit(Display.getCurrent());
	protected Text txtCode;
	protected Text txtUID;
	protected Text txtTagName;
	protected Text txtManufacturerName;
	protected Text txtLibraryCountry;
	protected Text txtISIL;
	protected Text txtCRC;
	protected Button btnEmpty;
	protected List lstUIDs;
	protected Text parts;
	protected Text part;
	protected Combo cbUsage;
	protected Canvas bookCanvas;
	protected Image bookImage = null;
	protected byte[] data = null;
	protected FinnishDataModel fdm = null;

	/**
	 * Create the composite.
	 * 
	 * @param parent
	 * @param style
	 * @param background
	 */
	public MainDialog(Composite parent, int style, Image logo, Image bgImage, int minTreshold, int maxTreshold) {
		super(parent, style);
		setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		this.logo = logo;
		this.bgImage = bgImage;
		this.data = null;
		this.maxTreshold = maxTreshold;
		this.minTreshold = minTreshold;
		addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				toolkit.dispose();
			}
		});
		toolkit.adapt(this);
		toolkit.setBackground(null);
		toolkit.paintBordersFor(this);
		setLayout(null);

		if (bgImage != null)
			this.setBackgroundImage(bgImage);

		Button btnClear = new Button(this, SWT.NONE);
		btnClear.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("delete");
				delete = txtUID.getText();
			}
		});
		btnClear.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		btnClear.setBounds(547, 547, 165, 43);
		toolkit.adapt(btnClear, true, true);
		btnClear.setText("Delete");

		txtCode = new Text(this, SWT.BORDER);
		txtCode.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtCode.setBounds(225, 318, 487, 41);
		toolkit.adapt(txtCode, true, true);

		Label lblNewLabel_1 = new Label(this, SWT.NONE);
		lblNewLabel_1.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblNewLabel_1.setAlignment(SWT.RIGHT);
		lblNewLabel_1.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblNewLabel_1.setBounds(10, 321, 209, 38);
		toolkit.adapt(lblNewLabel_1, true, true);
		lblNewLabel_1.setText("Primary item ID");

		Label lblUid = new Label(this, SWT.NONE);
		lblUid.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblUid.setText("UID");
		lblUid.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblUid.setAlignment(SWT.RIGHT);
		lblUid.setBounds(10, 274, 209, 38);
		toolkit.adapt(lblUid, true, true);

		txtUID = new Text(this, SWT.BORDER);
		txtUID.setEnabled(false);
		txtUID.setEditable(false);
		txtUID.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtUID.setBounds(225, 271, 487, 41);
		toolkit.adapt(txtUID, true, true);

		Label lblTagname = new Label(this, SWT.NONE);
		lblTagname.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblTagname.setText("Tag Name");
		lblTagname.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblTagname.setAlignment(SWT.RIGHT);
		lblTagname.setBounds(10, 227, 209, 38);
		toolkit.adapt(lblTagname, true, true);

		txtTagName = new Text(this, SWT.BORDER);
		txtTagName.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtTagName.setEnabled(false);
		txtTagName.setEditable(false);
		txtTagName.setBounds(225, 224, 487, 41);
		toolkit.adapt(txtTagName, true, true);

		Label lblManufacturerName = new Label(this, SWT.NONE);
		lblManufacturerName.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblManufacturerName.setText("Manufacturer Name");
		lblManufacturerName.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblManufacturerName.setAlignment(SWT.RIGHT);
		lblManufacturerName.setBounds(10, 179, 209, 38);
		toolkit.adapt(lblManufacturerName, true, true);

		txtManufacturerName = new Text(this, SWT.BORDER);
		txtManufacturerName.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtManufacturerName.setEnabled(false);
		txtManufacturerName.setEditable(false);
		txtManufacturerName.setBounds(225, 176, 487, 41);
		toolkit.adapt(txtManufacturerName, true, true);

		Label lblIsil = new Label(this, SWT.NONE);
		lblIsil.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblIsil.setText("Library Country");
		lblIsil.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblIsil.setAlignment(SWT.RIGHT);
		lblIsil.setBounds(10, 457, 209, 38);
		toolkit.adapt(lblIsil, true, true);

		txtLibraryCountry = new Text(this, SWT.BORDER);
		txtLibraryCountry.setEnabled(false);
		txtLibraryCountry.setEditable(false);
		txtLibraryCountry.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtLibraryCountry.setBounds(225, 454, 487, 41);
		toolkit.adapt(txtLibraryCountry, true, true);

		txtISIL = new Text(this, SWT.BORDER);
		txtISIL.setEnabled(false);
		txtISIL.setEditable(false);
		txtISIL.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtISIL.setBounds(225, 500, 487, 41);
		toolkit.adapt(txtISIL, true, true);

		Label label = new Label(this, SWT.NONE);
		label.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		label.setText("ISIL");
		label.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		label.setAlignment(SWT.RIGHT);
		label.setBounds(10, 503, 209, 38);
		toolkit.adapt(label, true, true);

		lstUIDs = new List(this, SWT.BORDER);
		lstUIDs.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lstUIDs.setEnabled(false);
		lstUIDs.setBounds(739, 530, 348, 60);
		toolkit.adapt(lstUIDs, true, true);

		Button btnSave = new Button(this, SWT.NONE);
		btnSave.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("save");
				store = txtUID.getText();
			}
		});
		btnSave.setText("Save");
		btnSave.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		btnSave.setBounds(376, 547, 165, 43);
		toolkit.adapt(btnSave, true, true);

		Label lblPart = new Label(this, SWT.NONE);
		lblPart.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblPart.setText("Part");
		lblPart.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblPart.setAlignment(SWT.RIGHT);
		lblPart.setBounds(10, 368, 209, 38);
		toolkit.adapt(lblPart, true, true);

		parts = new Text(this, SWT.BORDER);
		parts.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				String currentText = ((Text) e.widget).getText();
				try {
					int i = Integer.parseInt(currentText);
					if (i < 1 || i > 255) {
						MessageBox warn = new MessageBox(e.widget.getDisplay().getActiveShell(),
								SWT.ICON_WARNING | SWT.YES);
						warn.setMessage("Parts in item must be between 1 and 255");
						warn.open();
						if (i < 1)
							((Text) e.widget).setText("1");
						if (i > 255)
							((Text) e.widget).setText("255");
						((Text) e.widget).setFocus();
					}
				} catch (Exception ex) {
					MessageBox warn = new MessageBox(e.widget.getDisplay().getActiveShell(),
							SWT.ICON_WARNING | SWT.YES);
					warn.setMessage("Parts in item must be between 1 and 255");
					warn.open();
					((Text) e.widget).setText("1");
					((Text) e.widget).setFocus();
				}
			}
		});
		parts.setText("1");
		parts.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		parts.setBounds(330, 365, 63, 41);
		toolkit.adapt(parts, true, true);

		part = new Text(this, SWT.BORDER);
		part.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				String currentText = ((Text) e.widget).getText();
				try {
					int i = Integer.parseInt(currentText);
					int max = Integer.parseInt(parts.getText());
					if (i < 1 || i > max) {
						MessageBox warn = new MessageBox(e.widget.getDisplay().getActiveShell(),
								SWT.ICON_WARNING | SWT.YES);
						warn.setMessage("Parts in item must be between 1 and " + max);
						warn.open();
						if (i < 1)
							((Text) e.widget).setText("1");
						if (i > max)
							((Text) e.widget).setText(parts.getText());
						((Text) e.widget).setFocus();
					}
				} catch (Exception ex) {
					MessageBox warn = new MessageBox(e.widget.getDisplay().getActiveShell(),
							SWT.ICON_WARNING | SWT.YES);
					warn.setMessage("Parts in item must be between 1 and 255");
					warn.open();
					((Text) e.widget).setText("1");
					((Text) e.widget).setFocus();
				}
			}
		});
		part.setText("1");
		part.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		part.setBounds(225, 365, 63, 41);
		toolkit.adapt(part, true, true);

		Label lblOf = new Label(this, SWT.NONE);
		lblOf.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblOf.setText("of");
		lblOf.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblOf.setAlignment(SWT.CENTER);
		lblOf.setBounds(294, 368, 29, 38);
		toolkit.adapt(lblOf, true, true);

		cbUsage = new Combo(this, SWT.READ_ONLY);
		cbUsage.add("--", 0);
		cbUsage.add("Acquisition", 1);
		cbUsage.add("Item for circulation", 2);
		cbUsage.add("Item not for circulation - The patron must not be able to checkout", 3);
		cbUsage.add("Discarded item, this item must not be circulated", 4);
		cbUsage.add("Patron Card", 5);
		cbUsage.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		cbUsage.setBounds(225, 412, 487, 36);
		toolkit.adapt(cbUsage);
		toolkit.paintBordersFor(cbUsage);

		Label lblTypeOfUsage = new Label(this, SWT.NONE);
		lblTypeOfUsage.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblTypeOfUsage.setText("Type of Usage");
		lblTypeOfUsage.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblTypeOfUsage.setAlignment(SWT.RIGHT);
		lblTypeOfUsage.setBounds(10, 410, 209, 38);
		toolkit.adapt(lblTypeOfUsage, true, true);

		Canvas cvsHeading = new Canvas(this, SWT.NONE);
		cvsHeading.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		cvsHeading.setBounds(10, 10, 1100, 112);
		toolkit.adapt(cvsHeading);
		toolkit.paintBordersFor(cvsHeading);
		cvsHeading.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				Rectangle clientArea = cvsHeading.getClientArea();
				e.gc.drawImage(logo, 0, 0);
			}
		});

		bookCanvas = new Canvas(this, SWT.NONE);
		bookCanvas.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		bookCanvas.setBounds(739, 176, 348, 348);
		toolkit.adapt(bookCanvas);
		toolkit.paintBordersFor(bookCanvas);

		txtCRC = new Text(this, SWT.BORDER);
		txtCRC.setForeground(SWTResourceManager.getColor(SWT.COLOR_RED));
		txtCRC.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtCRC.setBounds(618, 365, 94, 41);
		toolkit.adapt(txtCRC, true, true);

		Label lblCrc = new Label(this, SWT.NONE);
		lblCrc.setText("CRC");
		lblCrc.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblCrc.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblCrc.setAlignment(SWT.CENTER);
		lblCrc.setBounds(566, 368, 45, 38);
		toolkit.adapt(lblCrc, true, true);

		btnEmpty = new Button(this, SWT.CHECK);
		btnEmpty.setEnabled(false);
		btnEmpty.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		btnEmpty.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		btnEmpty.setBounds(453, 365, 88, 41);
		toolkit.adapt(btnEmpty, true, true);
		btnEmpty.setText("Empty");

		Button btnCode = new Button(this, SWT.NONE);
		btnCode.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				String msg = "";
				if (data != null) {

					int line = 0;
					String txt = "";
					for (int i = 0; i < data.length; i++) {
						if (i % 4 == 0) {
							msg += txt + String.format("\n%02d:", line++);
							txt = "    ";
						}
						msg += String.format(" %02x", data[i]);
						if (data[i] < 32)
							txt += ".";
						else
							txt += (char) data[i];
					}
					msg += txt;
				} else
					msg = "no data";

				String blocks = "   ID | XOR | Data\n";
				blocks += "==================================================\n";
				if (fdm.getOptionalBlocks() != null) {
					for (FinnishDataModelOptionalBlock entry : fdm.getOptionalBlocks()) {
						blocks += String.format("% 5d | %02x  |", entry.getID(), entry.xorError() ? 0 : entry.getXOR());
						for (byte b : entry.getData()) {
							blocks += String.format(" %02x", b);
							blocks += "\n";
						}
					}
				}

				String std = "First Block\n";
				std += "===========\n";
				std += String.format("Version:          %d\n", fdm.getVersion());
				std += String.format("Type of usage:    %d\n", fdm.getTypeOfUsage());
				std += String.format("Parts in item:    %d\n", fdm.getPartsInItem());
				std += String.format("Part number:      %d\n", fdm.getPartNumber());
				std += String.format("Primary item id:  %s\n", fdm.getPrimaryItemId());
				std += String.format("CRC:              %02x %02x\n", fdm.getCRCBytes()[0], fdm.getCRCBytes()[1]);
				std += String.format("Country of Owner: %s\n", fdm.getCountryOfOwnerLib());
				std += String.format("ISIL:             %s\n", fdm.getISIL());

				Display display = getDisplay();
				Shell shell = new Shell(display);
				FillLayout layout = new FillLayout();
				shell.setLayout(layout);
				DetailDialog dd = new DetailDialog(shell, SWT.NONE, std, blocks, msg.trim());
				shell.setLocation(180, 140);
				shell.setSize(820, 600);
				shell.open();
				while (!shell.isDisposed()) {
					if (!display.readAndDispatch())
						display.sleep();
				}
				shell.dispose();
				/*
				 * MessageBox warn = new
				 * MessageBox(e.widget.getDisplay().getActiveShell(),
				 * SWT.ICON_INFORMATION | SWT.YES); warn.setMessage(msg);
				 * warn.open();
				 */
			}
		});
		btnCode.setText("#");
		btnCode.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		btnCode.setBounds(225, 547, 53, 43);
		toolkit.adapt(btnCode, true, true);

		bookCanvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if (bookImage != null) {
					Rectangle clientArea = bookCanvas.getClientArea();
					float clientAspect = (float) clientArea.width / clientArea.height;
					Rectangle imageBounds = bookImage.getBounds();
					float imageAspect = (float) imageBounds.width / imageBounds.height;

					int width = 0;
					int height = 0;

					if (clientAspect < imageAspect) {
						width = clientArea.width;
						height = (width * imageBounds.height) / imageBounds.width;
					} else {
						height = clientArea.height;
						width = (height * imageBounds.width) / imageBounds.height;
					}

					e.gc.drawImage(bookImage, 0, 0, bookImage.getBounds().width, bookImage.getBounds().height,
							(clientArea.width - width) / 2, (clientArea.height - height) / 2, width, height);
				}
			}
		});

		setTabList(new Control[] { txtCode, btnSave, part, parts, cbUsage, btnClear, txtUID, txtTagName,
				txtManufacturerName, txtLibraryCountry, txtISIL, lstUIDs });

	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public void setFinnishDataModel(FinnishDataModel fdm) {
		this.fdm = fdm;
	}

	protected String store = null;
	protected String delete = null;
	protected Image logo = null;
	protected Image bgImage = null;
	protected int minTreshold = 0;
	protected int maxTreshold = 0;
	protected int minCanny = 0;
	protected int maxCanny = 0;
}

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
package org.objectspace.rfid.library.inventory;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class InventoryDialog extends Composite {

	private final FormToolkit toolkit = new FormToolkit(Display.getCurrent());
	protected Text tInventoryTag;
	private Button bStartStop;
	private StyledText text;

	/**
	 * Create the composite.
	 * 
	 * @param parent
	 * @param style
	 */
	public InventoryDialog(Composite parent, int style, Image logo, Image bgImage) {
		super(parent, style);
		// setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		this.logo = logo;
		this.bgImage = bgImage;
		addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				toolkit.dispose();
			}
		});
		toolkit.adapt(this);
		toolkit.paintBordersFor(this);
		setLayout(null);

		Canvas cvsHeading = new Canvas(this, SWT.NONE);
		cvsHeading.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		cvsHeading.setBounds(0, 0, 1100, 112);
		cvsHeading.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				// Rectangle clientArea = cvsHeading.getClientArea();
				e.gc.drawImage(logo, 0, 0);
			}
		});
		toolkit.adapt(cvsHeading);
		toolkit.paintBordersFor(cvsHeading);

		Label lblInventoryTag = new Label(this, SWT.NONE);
		lblInventoryTag.setText("Inventory Tag");
		lblInventoryTag.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblInventoryTag.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblInventoryTag.setAlignment(SWT.RIGHT);
		lblInventoryTag.setBounds(0, 121, 138, 38);
		toolkit.adapt(lblInventoryTag, true, true);

		tInventoryTag = new Text(this, SWT.BORDER);
		tInventoryTag.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		tInventoryTag.setBounds(144, 118, 692, 41);
		toolkit.adapt(tInventoryTag, true, true);

		bStartStop = new Button(this, SWT.NONE);
		bStartStop.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!isRunning) {
					bStartStop.setText("Stop");
				} else {
					bStartStop.setText("Start");
				}
				isRunning = !isRunning;
			}
		});
		bStartStop.setText("Start");
		bStartStop.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		bStartStop.setBounds(842, 118, 138, 43);
		toolkit.adapt(bStartStop, true, true);

		text = new StyledText(this, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		text.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		text.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		text.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		text.setDoubleClickEnabled(false);
		text.setEditable(false);
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				text.setTopIndex(text.getLineCount() - 1);
			}
		});
		text.setBounds(0, 165, 1100, 449);
		toolkit.adapt(text);
		toolkit.paintBordersFor(text);
		
		txtCounter = new Text(this, SWT.BORDER);
		txtCounter.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		txtCounter.setBounds(986, 118, 114, 41);
		toolkit.adapt(txtCounter, true, true);
		
	}

	public void print(String t, int c1, int c2) {
		text.append(t);
		if (text.getLineCount() > 500) {
			String txt = text.getText();
			String[] lines = txt.split("\\r?\\n");
			txt = "";
			for (int i = lines.length - 400; i < lines.length; i++) {
				txt += lines[i] + "\n";
			}
			text.setText(txt);
		}
		txtCounter.setText(String.format("%d / %d", c1, c2));
	}

	public void println(String t, int c1, int c2) {
		print(t + "\n", c1, c2);
	}

	public boolean isRunning() {
		return isRunning;
	}

	protected Image logo = null;
	protected Image bgImage = null;
	protected boolean isRunning = false;
	private Text txtCounter;
}

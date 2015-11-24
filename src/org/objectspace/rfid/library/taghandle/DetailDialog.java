package org.objectspace.rfid.library.taghandle;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.SWT;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class DetailDialog extends Composite {
	private Text txtHexdump;
	private Text txtOptionalBlocks;

	/**
	 * Create the composite.
	 * @param parent
	 * @param style
	 */
	public DetailDialog(Composite parent, int style, String optionalBlocks, String rawData ) {
		super(parent, style);
		
		txtHexdump = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		txtHexdump.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		txtHexdump.setEnabled(true);
		txtHexdump.setEditable(false);
		txtHexdump.setBounds(535, 49, 255, 492);
		txtHexdump.setText(rawData);
		
		txtOptionalBlocks = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		txtOptionalBlocks.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		txtOptionalBlocks.setEnabled(true);
		txtOptionalBlocks.setEditable(false);
		txtOptionalBlocks.setBounds(10, 49, 519, 492);
		txtOptionalBlocks.setText(optionalBlocks);
		
		Label lblOptionalBlocks = new Label(this, SWT.NONE);
		lblOptionalBlocks.setText("Optional Blocks");
		lblOptionalBlocks.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblOptionalBlocks.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblOptionalBlocks.setAlignment(SWT.LEFT);
		lblOptionalBlocks.setBounds(10, 10, 157, 38);
		
		Label lblRawData = new Label(this, SWT.NONE);
		lblRawData.setText("Raw Data");
		lblRawData.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		lblRawData.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		lblRawData.setAlignment(SWT.LEFT);
		lblRawData.setBounds(535, 10, 118, 38);

	}

	@Override
	protected void checkSubclass() {
		// Disable the check that prevents subclassing of SWT components
	}
}

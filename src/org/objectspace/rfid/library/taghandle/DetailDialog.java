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
	private Text txtFinnishData;
	private Label label;

	/**
	 * Create the composite.
	 * 
	 * @param parent
	 * @param style
	 * @param string
	 */
	public DetailDialog(Composite parent, int style, String optionalBlocks, String std, String optionalBlock) {
		super(parent, style);

		txtHexdump = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		txtHexdump.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		txtHexdump.setEnabled(true);
		txtHexdump.setEditable(false);
		txtHexdump.setBounds(535, 49, 255, 492);
		txtHexdump.setText(optionalBlock);

		Label lblOptionalBlocks = new Label(this, SWT.NONE);
		lblOptionalBlocks.setText("First Block");
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

		txtFinnishData = new Text(this, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
		txtFinnishData.setText("<dynamic>");
		txtFinnishData.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		txtFinnishData.setEnabled(true);
		txtFinnishData.setEditable(false);
		txtFinnishData.setText(std);
		txtFinnishData.setBounds(10, 278, 519, 263);

		txtOptionalBlocks = new Text(this, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		txtOptionalBlocks.setFont(SWTResourceManager.getFont("Courier", 10, SWT.NORMAL));
		txtOptionalBlocks.setEnabled(true);
		txtOptionalBlocks.setEditable(false);
		txtOptionalBlocks.setBounds(10, 49, 519, 179);
		txtOptionalBlocks.setText(optionalBlocks);
		
		label = new Label(this, SWT.NONE);
		label.setText("Optional Blocks");
		label.setFont(SWTResourceManager.getFont("Segoe UI", 12, SWT.NORMAL));
		label.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		label.setAlignment(SWT.LEFT);
		label.setBounds(10, 234, 157, 38);


	}

	@Override
	protected void checkSubclass() {
		// Disable the check that prevents subclassing of SWT components
	}
}

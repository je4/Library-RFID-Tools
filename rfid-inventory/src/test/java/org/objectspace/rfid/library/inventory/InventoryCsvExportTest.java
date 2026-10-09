package org.objectspace.rfid.library.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class InventoryCsvExportTest {

	@Test
	@DisplayName("Test CSV export creates file, directory and valid content with BOM")
	public void testCsvExport(@TempDir File tempDir) throws Exception {
		File subFolder = new File(tempDir, "nested/export");
		File csvFile = new File(subFolder, "test_inventory.csv");

		List<InventoryItemEntry> items = new ArrayList<>();
		InventoryItemEntry item1 = new InventoryItemEntry();
		item1.index = 1;
		item1.time = "14:55:00";
		item1.primaryItemId = "30111234";
		item1.signature = "Signatur \"A\"";
		item1.partNumber = 1;
		item1.partsInItem = 1;
		item1.isil = "CH-000001-1";
		item1.country = "CH";
		item1.usageType = 1;
		item1.marker = "Regal 1";
		item1.uid = "E00401501234ABCD";
		item1.crcStatus = "OK";
		item1.manufacturer = "NXP";
		item1.tagName = "I-Code SLIX";
		item1.statusDetails = "OK";
		items.add(item1);

		InventoryModernFrame.writeCsv(csvFile, items);

		assertThat(csvFile).exists();
		byte[] bytes = Files.readAllBytes(csvFile.toPath());
		// Verify UTF-8 BOM: 0xEF, 0xBB, 0xBF
		assertThat(bytes.length).isGreaterThan(3);
		assertThat(bytes[0]).isEqualTo((byte) 0xEF);
		assertThat(bytes[1]).isEqualTo((byte) 0xBB);
		assertThat(bytes[2]).isEqualTo((byte) 0xBF);

		String content = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
		assertThat(content).contains("Status;Nr;Uhrzeit;Barcode_ID;Signatur;Teil_Nr;Teile_Gesamt;ISIL;Land;Nutzungsart;Standort_Marker;RFID_UID;CRC_Status;Hersteller;Transponder_Typ;Status_Details");
		assertThat(content).contains("\"30111234\"");
		assertThat(content).contains("\"Signatur \"\"A\"\"\"");
		assertThat(content).contains("\"E00401501234ABCD\"");
	}
}

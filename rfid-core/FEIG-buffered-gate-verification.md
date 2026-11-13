# FEIG Buffered-Gate: offener Sicherheitsnachweis

Die vorliegende Reader-Information nennt `ID LR2500-A` (Reader Type 44), RF-Firmware `01.01.00`, Application-Firmware `02.05.00` und DHCP-Adresse `10.96.12.19`. Der SDK-Export vom 13.11.2026 identifiziert auch das separat genannte Ziel `10.96.12.18:10001` als LR2500-A (Device-ID `1C6CB24D`). ISOStart zeigte für `0x22` und `0x2B` jeweils „Command not available“; Herkunft der Meldungen, Readerprofil und aktive Betriebsart sind nicht geklärt. Der Export belegt weder die aktive Betriebsart noch die Sicherheit der Pufferbefehle.

## Quellen und Grenzen

- Das [LR2500-Datenblatt](https://www.rfidglobal.it/download/sales/datasheet_apparati_rfid/rfid_passivi_hf/ID_ISC.LR2500_RFID_Global.pdf) nennt Buffered Read Mode für den LR2500-A, aber keine geräte- und firmwarebezogene Befehlssemantik.
- Die SDK-Dokumentation `FEIG.ID.SDK.Gen3.Java-v7.1.0/doc/FEIG-ID-SDK-DOC-linux-v7.1.0/resources/app/src/api/introduction-7.1.0.json`, Abschnitt `automatic-reader-modes`, beschreibt `Brm.readReaderBuffer` als Übertragung in die SDK-Warteschlange und `clearReaderBuffer` als Löschen übertragener Datensätze. `initializeReaderBuffer` verwirft auch ungelesene Daten. Die Beispiele unterscheiden `Brm`/`0x22` und `TagEvent`/`0x2B`, ohne die Zuordnung zum LR2500-A zu belegen.
- Das [ISOStart-2019-Handbuch](https://www.thermo-tex.de/assets/Uploads/module/Produkte/transponder/downloads/Lesesysteme/HF/Software/H80301-e-ID-B.pdf), Abschnitt 5.4.4, beschreibt allgemein `0x21`/`0x22` zum Lesen und `0x32` zum Löschen übertragener Datensätze; es verifiziert weder die konkrete Firmware noch den vollständigen SDK-Ablauf.
- `FeigBufferedGateHardwareTest` führt `FeigRFID.connect()`, Counter-Abfragen, `readReaderBufferInfo`, `readReaderBuffer`, `popItem` und das Schließen aus. `connect()` ruft unter anderem `ReaderModule.connect()` und `readReaderInfo()` auf. Für deren vollständige geräteseitige Nebenwirkungsfreiheit und die übrigen Testoperationen liegt kein ausreichender Nachweis vor; den Test nicht aktivieren.

## Anfrage an FEIG

Für einen `ID LR2500-A` mit RF-Firmware `01.01.00` und Application-Firmware `02.05.00`:

1. Welches Systemhandbuch und welche Befehlsfamilie gelten für gepufferte Tag-Datensätze? Welche Bedeutung kann die ISOStart-Anzeige „Command not available“ bei `0x22` und `0x2B` haben?
2. Welche Betriebsart, Readerprofil und Einstellungen sind Voraussetzung, und welche Datenfelder und Zeitstempelsemantik liefert der Puffer?
3. Welche konkreten Identifikations-, Pufferstatus-, Pufferlese- und People-Counter-Befehle sind einschließlich Verbindung und Schließen nachweislich nebenwirkungsfrei? Bitte auch die entsprechenden SDK-Methoden und eventuelle automatische Aktionen benennen.
4. Werden Datensätze bei wiederholtem Lesen unverändert erneut geliefert? Was geschieht bei vollem Puffer ohne Bestätigung oder Löschung?

Bis zur Klärung darf der Buffered-Betrieb auf diesem Gate nicht aktiviert werden; diese betriebliche Sperre wird nicht durch einen Software-Lock erzwungen. `autoconfig` ist standardmäßig deaktiviert; bei expliziter Aktivierung schreibt es die modusabhängige Konfiguration und löst einen Reset aus. Die Referenzdatei ist eine gerätespezifische Sicherung, keine Sicherheitsfreigabe. Ohne Pufferfreigabe sind Wiederholungen und Überlauf möglich; dauerhafter verlustfreier Betrieb ist nicht belegt.
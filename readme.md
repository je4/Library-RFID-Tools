# RFID Inventory - info-age GmbH, Basel

This application supports ISO 15693 compatible RFID tags written according to ISO 28560-2 / "Finnish Data Model".

## License & Provenance
* **Maintainer / Copyright**: Copyright 2015-2026 info-age GmbH, Basel.
* **Original System**: Based on HAWK RFID Library Tools (Copyright 2015 Center for Information, Media and Technology (ZIMT), HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen).
* **License**: GNU General Public License v3 or later (GPLv3+).

## Libraries & SDKs
The following libraries/SDKs are used under the Windows Operating System:
* FEIG SDK Gen3 for Java (v7.1.0, located in `lib/fedm-*.jar` and `lib/native/x64/` with Gen3 architecture support)
* Apache Commons (configuration2 2.10.1, collections4 4.4, lang3 3.17.0)
* SLF4J (2.0.16) / Logback (1.5.12)
* SWT: Standard Widget Toolkit (Windows x64)
* FlatLaf (3.5.4): Modern Look and Feel für Swing (Dark/Light/System Theme, High-DPI Skalierung)
* MySQL Connector/J (8.4.0 LTS JDBC driver)

## Build and Modernization
* Maven build system configured with Java 25 compatibility (`--release 25`).
* FEIG SDK Gen3 (v7.1.0) integration (x64 native support).
* Parallele moderne Swing/FlatLaf- und SWT-Oberfläche, wählbar über `inventory.xml` (`<ui>modern</ui>` oder `<ui>swt</ui>`).
* High-performance responsive GUI with real-time hardware status detection, visual scan feedback, KPI metrics cards, and live search.
* ISO 28560 / Finnish Data Model unit tests.

## Tools & Installation
### Inventory
The Inventory Tool is used to mass-read RFIDs, verify media signatures, and write contents into a SQL database system or export to CSV.

* **Development Start**: `run-inventory.ps1` (or `run-inventory.bat`) compiles and starts the Inventory application directly from the source repository.
* **Windows Installer Build**: `build-installer.ps1` (or `build-installer.bat`) packages all dependencies, creates a self-contained runtime with embedded Java (via `jpackage`), and compiles the single-file setup installer:
  * Setup-Datei: `dist\RFID-Inventory-Setup-1.0.1.exe`
  * Portable Distribution: `target\dist\RFID-Inventory\`

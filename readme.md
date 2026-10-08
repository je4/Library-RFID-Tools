# RFID Inventory - info-age GmbH, Basel

This application supports ISO 15693 compatible RFID tags written according to ISO 28560-2 / "Finnish Data Model".

## License & Provenance
* **Maintainer / Copyright**: Copyright 2015-2026 info-age GmbH, Basel.
* **Original System**: Based on HAWK RFID Library Tools (Copyright 2015 Center for Information, Media and Technology (ZIMT), HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen).
* **License**: GNU General Public License v3 or later (GPLv3+).

## Libraries & SDKs
The following libraries/SDKs are used under the Windows Operating System:
* FEIG SDK for JAVA (v5.6.3, located in `ID_ISC.SDK.Java.Win-V5.6.3`, for FEIG ISC.MR102-USB support) 
  * fecom.dll, fefu.dll, feisc.dll, fetcl.dll, fetcp.dll, feusb.dll, OBIDISC4J.dll
  * OBIDISC4J.jar, OBIDISC4J_API.jar
* Apache Commons (configuration2, collections4, lang3)
* SLF4J / Logback (Logging)
* SWT: Standard Widget Toolkit (Windows x64)
* MySQL Connector/J (JDBC driver)

## Build and Modernization
* Maven build system configured with Java 25 compatibility.
* FEIG SDK v5.6.3 integration (x64 native support).
* High-performance responsive GUI with real-time hardware status detection.
* ISO 28560 / Finnish Data Model unit tests.

## Tools & Installation
### Inventory
The Inventory Tool is used to mass-read RFIDs, verify media signatures, and write contents into a SQL database system or export to CSV.

* **Development Start**: `run-inventory.ps1` (or `run-inventory.bat`) compiles and starts the Inventory application directly from the source repository.
* **Windows Installer Build**: `build-installer.ps1` (or `build-installer.bat`) packages all dependencies, creates a self-contained runtime with embedded Java (via `jpackage`), and compiles the single-file setup installer:
  * Setup-Datei: `dist\RFID-Inventory-Setup-1.0.1.exe`
  * Portable Distribution: `target\dist\RFID-Inventory\`

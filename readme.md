# Library RFID Tools

**Library RFID Tools** is a modernized Java-based multi-module software suite and hardware integration toolkit for library RFID management, ISO 15693 tag processing, and FEIG electronic RFID reader control.

The system implements the **ISO 28560-2** library RFID standard ("Finnish Data Model") and integrates with the **FEIG SDK Gen3 (v7.1.0)** for high-throughput scanning, hardware diagnostic monitoring, and firmware configuration management.

---

## License & Provenance

* **Maintainer / Copyright**: Copyright 2015-2026 info-age GmbH, Basel.
* **Original System**: Based on HAWK RFID Library Tools (Copyright 2015 Center for Information, Media and Technology (ZIMT), HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen).
* **License**: GNU General Public License v3 or later (GPLv3+).

---

## Project Modules & Repository Overview

```
Library-RFID-Tools/
├── pom.xml                                  # Root Maven multi-module POM (Java 25)
├── readme.md                                # Top-level project documentation
├── lib/                                     # Bundled FEIG SDK Gen3 JARs & native binaries
│   ├── fedm-java-api-7.1.0.jar              # FEIG Java API (Gen3)
│   ├── fedm-funit-java-api-1.1.0.jar        # FEIG Functional Unit API
│   ├── fedm-service-java-api-11.2.1.jar     # FEIG Service API
│   ├── native/x64/                          # 64-bit Windows FEIG native driver DLLs
│   └── ext/                                 # Staged third-party dependency JARs
├── FEIG.ID.SDK.Gen3.Java-v7.1.0/            # Upstream FEIG Java Gen3 SDK distribution
├── FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/   # FEIG Raspberry Pi aarch64 SDK distribution
├── ID_ISC.SDK.Java.Win-V5.6.3/              # Legacy FEIG SDK reference
└── rfid-inventory/                          # RFID Inventory Scanning Application & Tools
    ├── pom.xml                              # Inventory module POM
    ├── readme.md                            # Inventory application documentation
    ├── run-inventory.ps1                    # Application development startup script
    ├── build-installer.ps1                  # Windows installer build script
    ├── backup-firmware.ps1                  # Reader firmware/EEPROM backup tool
    ├── restore-firmware.ps1                 # Reader firmware/EEPROM restore tool
    ├── installer.iss                        # Inno Setup 6 installer script
    ├── inventory.xml.template               # Default XML configuration template
    └── src/                                 # Application source code and unit tests
```

---

## Modules

### 1. [RFID Inventory](rfid-inventory/readme.md) (`rfid-inventory`)
Desktop inventory scanning tool for ISO 15693 RFID tags.
* **Dual UI**: Modern Swing / FlatLaf interface with Dark/Light theme support, live search, KPI metrics cards, tag inspector, and hex dump analysis, or classic SWT native UI.
* **Data Logging**: Real-time export to SQL databases (MySQL/MariaDB) or RESTful webservices with HMAC-SHA256 JWT authorization, plus CSV export.
* **Hardware & Firmware Management**: Built-in scripts to backup and restore full EEPROM/RAM hardware parameter sets on FEIG readers (`backup-firmware.ps1`, `restore-firmware.ps1`).
* **Packaging**: Self-contained Windows runtime packaging (`jpackage`) and Inno Setup installer generator (`build-installer.ps1`).

For detailed usage, configuration, and build instructions, see [rfid-inventory/readme.md](rfid-inventory/readme.md).

---

## Technical Stack & Dependencies

* **Language & Runtime**: Java 25 (LTS compatibility with `--release 25`)
* **RFID Hardware SDK**: FEIG SDK Gen3 for Java (v7.1.0) with x64 native driver DLLs
* **UI Frameworks**:
  * [FlatLaf 3.5.4](https://www.formdev.com/flatlaf/) for modern responsive Swing GUI
  * Eclipse SWT (Standard Widget Toolkit, Windows x64) for classic interface
* **Configuration & Utilities**: Apache Commons (Configuration2 2.10.1, Collections4 4.4, Lang3 3.17.0)
* **Logging**: SLF4J 2.0.16 & Logback 1.5.12
* **Database Driver**: MySQL Connector/J 8.4.0 LTS
* **Testing**: JUnit 5 (Jupiter 5.10.2) & AssertJ (3.25.3)

---

## Prerequisites

* **Operating System**: Windows 10 / 11 (x64)
* **Java Development Kit**: JDK 25 or later (with `JAVA_HOME` environment variable configured)
* **Maven**: Apache Maven 3.9+ (or an IDE with integrated Maven support)
* **Inno Setup 6** *(optional)*: Required only when compiling the Windows installation package (`.exe`)

---

## Building the Project

### Using Maven

From the project root:

```bash
# Compile and run test suite across all modules
mvn clean test

# Build package artifacts
mvn clean package
```

### Running Tests

Execute tests with Maven or via the standalone test runner:

```powershell
# Run the standalone TestRunner across all test suites
java -cp "rfid-inventory\target\classes;rfid-inventory\target\test-classes;lib\*;lib\ext\*" org.objectspace.rfid.TestRunner
```

---

## Quick Start: RFID Inventory

To start the RFID Inventory application directly from the source repository:

```powershell
cd rfid-inventory
.\run-inventory.ps1
```

To build a standalone Windows setup installer:

```powershell
cd rfid-inventory
.\build-installer.ps1 -AppVersion "1.0.6"
```

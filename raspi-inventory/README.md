# Raspi Inventory - Headless Continuous RFID Daemon

**Raspi Inventory** ist ein leichtgewichtiger, autonomer RFID-Dienst für Raspberry Pi 4 / 5 (Linux `aarch64`), der FEIG Electronic RFID-Leser (USB) steuert und RFID-Medien (ISO 15693 / ISO 28560-2) kontinuierlich scannt und verarbeitet.

---

## 1. Systemvoraussetzungen (Raspberry Pi)

* **Hardware**: Raspberry Pi 4 / 5 (64-Bit OS / `aarch64`)
* **Betriebssystem**: Raspberry Pi OS 64-Bit (Debian Bookworm oder Bullseye)
* **Java Runtime**: OpenJDK 21 oder OpenJDK 25 (Headless JRE genügt):
  ```bash
  sudo apt update
  sudo apt install -y openjdk-21-jre-headless
  ```
* **Hardware-Zugriffsrechte**: Der Benutzer muss Zugriff auf USB-Geräte haben (z. B. Gruppe `plugdev` oder `dialout`).

---

## 2. Installation

Das Distributions-Paket kann an beliebiger Stelle entpackt werden (empfohlen: `/opt/raspi-inventory`):

```bash
# Zielverzeichnis anlegen
sudo mkdir -p /opt/raspi-inventory
sudo chown -R $USER:$USER /opt/raspi-inventory

# Distributionsarchiv entpacken
tar -xzf raspi-inventory-2.0.0-SNAPSHOT-linux-aarch64.tar.gz -C /opt/
# oder
unzip raspi-inventory-2.0.0-SNAPSHOT-linux-aarch64.zip -d /opt/

cd /opt/raspi-inventory
chmod +x run-raspi-inventory.sh lib/native/aarch64/*.sh lib/native/aarch64/*.so*
```

---

## 3. Konfiguration

Die Datei `inventory.xml` steuert die Schnittstellen und Parameter:

* **RFID-Leser**: Standardmäßig USB (`<type>usb</type>`).
* **Webservice**: Automatische Übertragung gelesener Medien an einen REST-Endpunkt (mit optionaler JWT-Signierung).
* **Datenbank**: Direkter SQL-Export (MySQL / MariaDB).
* **Scan-Zyklus & Debounce**:
  * Scan-Intervall: `<sleep>300</sleep>` (in ms)
  * Debounce-Fenster: Standard 3000 ms (unterdrückt Mehrfachmeldungen solange ein Tag im Antennenfeld liegt).

---

## 4. Manuelles Starten

Über das mitgelieferte Run-Script:

```bash
# Standard-Start (verwendet inventory.xml im aktuellen Verzeichnis)
./run-raspi-inventory.sh

# Mit expliziter Konfigurationsdatei
./run-raspi-inventory.sh -c /opt/raspi-inventory/inventory.xml

# Mit CLI-Parametern (z.B. Debounce auf 5s, Scan-Intervall auf 200ms)
./run-raspi-inventory.sh -c inventory.xml --debounce 5000 --sleep 200
```

### Befehlszeilenoptionen:
* `-c, --config <file>`: Pfad zur `inventory.xml` (Standard: `inventory.xml`)
* `-d, --debounce <ms>`: Debounce-Zeitfenster in ms (Standard: `3000`)
* `-s, --sleep <ms>`: Pause zwischen Scan-Durchläufen in ms (Standard: `300`)
* `-b, --blocks <num>`: Anzahl der zu lesenden ISO 15693 Datenblöcke (Standard: `12`)
* `-v, --version`: Versionsnummer ausgeben
* `-h, --help`: Hilfe anzeigen

---

## 5. Einrichtung als Systemd-Hintergrunddienst

Für den automatischen Start beim Booten des Raspberry Pi:

```bash
# Service-Datei kopieren
sudo cp /opt/raspi-inventory/raspi-inventory.service /etc/systemd/system/

# Systemd neu laden
sudo systemctl daemon-reload

# Dienst aktivieren und starten
sudo systemctl enable raspi-inventory.service
sudo systemctl start raspi-inventory.service

# Status und Log-Ausgaben prüfen
sudo systemctl status raspi-inventory.service
sudo journalctl -u raspi-inventory.service -f
```

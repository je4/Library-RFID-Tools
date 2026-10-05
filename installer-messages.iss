[CustomMessages]
german.CreateStartMenuIcon=Startmenü-Eintrag erstellen
german.ServiceChoicePageTitle=Schnittstellen und Anbindungen
german.ServiceChoicePageSubTitle=Wählen Sie die zu aktivierenden Schnittstellen aus.
german.ServiceChoicePageDesc=Hier können Sie festlegen, welche externen Schnittstellen und Dienste für die Inventarisierung aktiviert werden sollen:
german.DbEnableCheck=Datenbankanbindung aktivieren (SQL / MySQL / MariaDB)
german.DbEnableHelp=Ermöglicht das Speichern und Protokollieren von Inventarisierungsdaten in einer relationalen SQL-Datenbank.
german.WsEnableCheck=Webservice-Anbindung aktivieren (HTTP REST API)
german.WsEnableHelp=Überträgt erfasste RFID-Tags per Webservice in Echtzeit an einen zentralen Server.

german.DbDsnPageTitle=Datenbank-Verbindung
german.DbDsnPageSubTitle=Geben Sie die Verbindungszeichenfolge (DSN) für die SQL-Datenbank an.
german.DbDsnPageDesc=Geben Sie die JDBC-Verbindungszeichenfolge ein, über die sich die Anwendung mit Ihrer Datenbank verbindet:
german.DbDsnLabel=JDBC-Datenbank-DSN:
german.DbDsnHelp=Beispiel: jdbc:mysql://localhost/rfid?user=rfid&password=XXX

german.WsPageTitle=Webservice-Anbindung
german.WsPageSubTitle=Geben Sie die Webservice-Ziel-URL und den Authentifizierungsschlüssel an.
german.WsPageDesc=Hier können Sie die Ziel-URL für die Webservice-Übertragung der RFID-Erfassungen sowie den optionalen JWT-Schlüssel konfigurieren:
german.WsTargetUrlLabel=Webservice-Ziel-URL (target_url):
german.WsTargetUrlHelp=Beispiel: https://httpbin.org/get
german.WsJwtKeyLabel=JWT-Schlüssel (jwt_key):
german.WsJwtKeyHelp=Geheimer Schlüssel für die HMAC-SHA256 Authentifizierung (optional).

german.FinishedConfigInfo=Pfad zur Konfigurationsdatei (inventory.xml):

german.ExistingInstallSameHeading=Bestehende Installation gefunden
german.ExistingInstallSamePrompt=Auf Ihrem Computer ist %1 bereits in der Version %2 installiert.%n%nInstallationspfad: %3%n%nSie können das Setup erneut ausführen (um Einstellungen anzupassen oder Komponenten zu reparieren) oder die bestehende Installation deinstallieren.
german.ExistingInstallBtnSetup=Setup / Konfiguration erneut ausführen
german.ExistingInstallBtnUninstall=Bestehende Version deinstallieren
german.ExistingInstallBtnCancel=Setup abbrechen

german.ExistingInstallUpgradeHeading=Ältere Version gefunden (Update verfügbar)
german.ExistingInstallUpgradePrompt=Auf Ihrem Computer ist %1 in Version %2 installiert.%n%nInstallationspfad: %3%n%nMöchten Sie die bestehende Installation auf Version %4 aktualisieren oder die bisherige Version vorab deinstallieren?
german.ExistingInstallBtnUpdate=Auf Version %1 aktualisieren (empfohlen)

german.ExistingInstallNewerErrorHeading=Neuere Version bereits installiert
german.ExistingInstallNewerErrorMsg=Auf Ihrem Computer ist bereits eine neuere Version von %1 (Version %2) installiert.%n%nInstallationspfad: %3%n%nEin Downgrade auf die ältere Version %4 wird nicht unterstützt.%nBitte deinstallieren Sie zuerst die bestehende Version oder verwenden Sie ein passendes Setup-Paket.

german.UninstallSuccess=Die bisherige Version wurde erfolgreich deinstalliert.%n%nMöchten Sie das Setup für eine Neuinstallation jetzt fortsetzen?
german.UninstallError=Das Deinstallationsprogramm konnte nicht ausgeführt werden oder wurde abgebrochen (Fehlercode: %1).
german.UninstallNotFound=Das Deinstallationsprogramm wurde nicht gefunden:%n%1%n%nDas Setup wird regulär fortgesetzt.

english.CreateStartMenuIcon=Create a Start Menu shortcut
english.ServiceChoicePageTitle=Interfaces & Integrations
english.ServiceChoicePageSubTitle=Select the interfaces and integrations to enable.
english.ServiceChoicePageDesc=Specify which external services and interfaces should be enabled for inventory processing:
english.DbEnableCheck=Enable database connection (SQL / MySQL / MariaDB)
english.DbEnableHelp=Enables logging and storing inventory tag data in a relational SQL database.
english.WsEnableCheck=Enable webservice connection (HTTP REST API)
english.WsEnableHelp=Dispatches scanned RFID tags via HTTP webservice to a central server in real-time.

english.DbDsnPageTitle=Database Connection
english.DbDsnPageSubTitle=Specify the connection string (DSN) for the SQL database.
english.DbDsnPageDesc=Enter the JDBC connection string used to connect to your database:
english.DbDsnLabel=JDBC Database DSN:
english.DbDsnHelp=Example: jdbc:mysql://localhost/rfid?user=rfid&password=XXX

english.WsPageTitle=Webservice Connection
english.WsPageSubTitle=Specify the webservice target URL and authentication key.
english.WsPageDesc=Configure the target URL for webservice dispatching of RFID scans and the optional JWT secret key:
english.WsTargetUrlLabel=Webservice Target URL (target_url):
english.WsTargetUrlHelp=Example: https://httpbin.org/get
english.WsJwtKeyLabel=JWT Secret Key (jwt_key):
english.WsJwtKeyHelp=Secret key for HMAC-SHA256 authentication (optional).

english.FinishedConfigInfo=Path to configuration file (inventory.xml):

english.ExistingInstallSameHeading=Existing Installation Found
english.ExistingInstallSamePrompt=%1 is already installed on your computer (Version %2).%n%nInstallation path: %3%n%nYou can run setup again (to reconfigure settings or repair files) or uninstall the existing installation.
english.ExistingInstallBtnSetup=Run Setup / Reconfigure
english.ExistingInstallBtnUninstall=Uninstall existing version
english.ExistingInstallBtnCancel=Cancel Setup

english.ExistingInstallUpgradeHeading=Older Version Found (Update Available)
english.ExistingInstallUpgradePrompt=%1 (Version %2) is installed on your computer.%n%nInstallation path: %3%n%nWould you like to update directly to version %4 or uninstall the previous version first?
english.ExistingInstallBtnUpdate=Update to version %1 (recommended)

english.ExistingInstallNewerErrorHeading=Newer Version Already Installed
english.ExistingInstallNewerErrorMsg=A newer version of %1 (Version %2) is already installed on your computer.%n%nInstallation path: %3%n%nDowngrading to older version %4 is not supported.%nPlease uninstall the newer version first or use a matching setup package.

english.UninstallSuccess=The previous version was successfully uninstalled.%n%nDo you want to continue with the setup for a clean reinstallation?
english.UninstallError=The uninstaller could not be executed or was aborted (Error code: %1).
english.UninstallNotFound=The uninstaller could not be found at:%n%1%n%nSetup will continue normally.

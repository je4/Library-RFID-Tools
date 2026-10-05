; Script generated for Inno Setup 6
; RFID Inventory - info-age GmbH, Basel

#define MyAppName "RFID Inventory"
#ifndef MyAppVersion
#define MyAppVersion "1.0.1"
#endif
#define MyAppPublisher "info-age GmbH, Basel"
#define MyAppExeName "RFID-Inventory.exe"
#define MyAppGuid "9C8B5F2A-4D71-4A5B-A28E-9872C1579D3B"

[Setup]
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppId={{{#MyAppGuid}}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\RFID-Inventory
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=RFID-Inventory-Setup-{#MyAppVersion}
SetupIconFile=app.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "german"; MessagesFile: "compiler:Languages\German.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

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

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startmenuicon"; Description: "{cm:CreateStartMenuIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Main application image and files (excludes local inventory.xml to prevent sensitive keys/passwords from being bundled)
Source: "target\dist\RFID-Inventory\*"; DestDir: "{app}"; Excludes: "inventory.xml"; Flags: ignoreversion recursesubdirs createallsubdirs
; Default configuration template (installed only if inventory.xml does not already exist)
Source: "inventory.xml.template"; DestDir: "{app}"; DestName: "inventory.xml"; Flags: onlyifdoesntexist
; Application icon
Source: "app.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startmenuicon
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"; Tasks: startmenuicon
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  ServiceChoicePage: TWizardPage;
  ServiceChoiceDescLabel: TLabel;
  DbEnableCheckBox: TNewCheckBox;
  DbEnableHelpLabel: TLabel;
  WsEnableCheckBox: TNewCheckBox;
  WsEnableHelpLabel: TLabel;

  DbDsnPage: TWizardPage;
  DbDsnDescLabel: TLabel;
  DbDsnLabel: TLabel;
  DbDsnEdit: TNewEdit;
  DbDsnHelpLabel: TLabel;

  WsPage: TWizardPage;
  WsDescLabel: TLabel;
  WsTargetUrlLabel: TLabel;
  WsTargetUrlEdit: TNewEdit;
  WsTargetUrlHelpLabel: TLabel;
  WsJwtKeyLabel: TLabel;
  WsJwtKeyEdit: TNewEdit;
  WsJwtKeyHelpLabel: TLabel;

  FinishedConfigLabel: TLabel;
  FinishedConfigEdit: TNewEdit;
  ConfigLoaded: Boolean;

function GetInstalledVersion(var OutVersion: String; var OutUninstallStr: String; var OutInstallPath: String): Boolean;
var
  AppKey: String;
begin
  Result := False;
  OutVersion := '';
  OutUninstallStr := '';
  OutInstallPath := '';
  AppKey := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{{#MyAppGuid}}_is1';

  if RegQueryStringValue(HKCU, AppKey, 'DisplayVersion', OutVersion) or
     RegQueryStringValue(HKLM, AppKey, 'DisplayVersion', OutVersion) or
     RegQueryStringValue(HKCU64, AppKey, 'DisplayVersion', OutVersion) or
     RegQueryStringValue(HKLM64, AppKey, 'DisplayVersion', OutVersion) or
     RegQueryStringValue(HKCU32, AppKey, 'DisplayVersion', OutVersion) or
     RegQueryStringValue(HKLM32, AppKey, 'DisplayVersion', OutVersion) then
  begin
    Result := True;
  end;

  if Result then
  begin
    if not RegQueryStringValue(HKCU, AppKey, 'UninstallString', OutUninstallStr) then
      if not RegQueryStringValue(HKLM, AppKey, 'UninstallString', OutUninstallStr) then
        if not RegQueryStringValue(HKCU64, AppKey, 'UninstallString', OutUninstallStr) then
          if not RegQueryStringValue(HKLM64, AppKey, 'UninstallString', OutUninstallStr) then
            if not RegQueryStringValue(HKCU32, AppKey, 'UninstallString', OutUninstallStr) then
              RegQueryStringValue(HKLM32, AppKey, 'UninstallString', OutUninstallStr);

    if not RegQueryStringValue(HKCU, AppKey, 'InstallLocation', OutInstallPath) then
      if not RegQueryStringValue(HKLM, AppKey, 'InstallLocation', OutInstallPath) then
        if not RegQueryStringValue(HKCU64, AppKey, 'InstallLocation', OutInstallPath) then
          if not RegQueryStringValue(HKLM64, AppKey, 'InstallLocation', OutInstallPath) then
            if not RegQueryStringValue(HKCU32, AppKey, 'InstallLocation', OutInstallPath) then
              RegQueryStringValue(HKLM32, AppKey, 'InstallLocation', OutInstallPath);

    if OutInstallPath = '' then
    begin
      if not RegQueryStringValue(HKCU, AppKey, 'Inno Setup: App Path', OutInstallPath) then
        if not RegQueryStringValue(HKLM, AppKey, 'Inno Setup: App Path', OutInstallPath) then
          if not RegQueryStringValue(HKCU64, AppKey, 'Inno Setup: App Path', OutInstallPath) then
            if not RegQueryStringValue(HKLM64, AppKey, 'Inno Setup: App Path', OutInstallPath) then
              if not RegQueryStringValue(HKCU32, AppKey, 'Inno Setup: App Path', OutInstallPath) then
                RegQueryStringValue(HKLM32, AppKey, 'Inno Setup: App Path', OutInstallPath);
    end;
  end;
end;

function CompareVersionNumbers(V1, V2: String): Integer;
var
  P1, P2: Integer;
  Part1, Part2: String;
  Num1, Num2: Integer;
begin
  Result := 0;
  while (V1 <> '') or (V2 <> '') do
  begin
    P1 := Pos('.', V1);
    if P1 > 0 then
    begin
      Part1 := Copy(V1, 1, P1 - 1);
      Delete(V1, 1, P1);
    end
    else
    begin
      Part1 := V1;
      V1 := '';
    end;

    P2 := Pos('.', V2);
    if P2 > 0 then
    begin
      Part2 := Copy(V2, 1, P2 - 1);
      Delete(V2, 1, P2);
    end
    else
    begin
      Part2 := V2;
      V2 := '';
    end;

    Num1 := StrToIntDef(Part1, 0);
    Num2 := StrToIntDef(Part2, 0);

    if Num1 < Num2 then
    begin
      Result := -1;
      Exit;
    end
    else if Num1 > Num2 then
    begin
      Result := 1;
      Exit;
    end;
  end;
end;

function PerformUninstall(const UninstallStr: String): Boolean;
var
  CleanUninstallStr: String;
  ResultCode: Integer;
begin
  Result := False;
  CleanUninstallStr := RemoveQuotes(UninstallStr);
  if (CleanUninstallStr <> '') and FileExists(CleanUninstallStr) then
  begin
    if Exec(CleanUninstallStr, '/SILENT', ExtractFileDir(CleanUninstallStr), SW_SHOW, ewWaitUntilTerminated, ResultCode) then
    begin
      if ResultCode = 0 then
      begin
        if MsgBox(CustomMessage('UninstallSuccess'), mbConfirmation, MB_YESNO) = IDYES then
          Result := True
        else
          Result := False;
      end
      else
      begin
        MsgBox(FmtMessage(CustomMessage('UninstallError'), [IntToStr(ResultCode)]), mbError, MB_OK);
        Result := False;
      end;
    end
    else
    begin
      MsgBox(FmtMessage(CustomMessage('UninstallError'), ['-1']), mbError, MB_OK);
      Result := False;
    end;
  end
  else
  begin
    MsgBox(FmtMessage(CustomMessage('UninstallNotFound'), [UninstallStr]), mbInformation, MB_OK);
    Result := True;
  end;
end;

function InitializeSetup(): Boolean;
var
  OldVersion, UninstallStr, InstallPath: String;
  VerCmp: Integer;
  MsgResult: Integer;
  PromptMsg: String;
  BtnLabels: TArrayOfString;
begin
  Result := True;

  { Fall 1: Anwendung nicht installiert -> regulaere Installation }
  if not GetInstalledVersion(OldVersion, UninstallStr, InstallPath) then
  begin
    Result := True;
    Exit;
  end;

  if OldVersion = '' then
    OldVersion := '1.0.0';

  VerCmp := CompareVersionNumbers(OldVersion, '{#MyAppVersion}');

  { Fall 4: Anwendung in hoeherer Version installiert -> Fehler und Abbruch }
  if VerCmp > 0 then
  begin
    MsgBox(FmtMessage(CustomMessage('ExistingInstallNewerErrorMsg'), ['{#MyAppName}', OldVersion, InstallPath, '{#MyAppVersion}']), mbError, MB_OK);
    Result := False;
    Exit;
  end;

  { Fall 2: Anwendung in gleicher Version installiert -> Angebot Setup oder Deinstallation }
  if VerCmp = 0 then
  begin
    PromptMsg := FmtMessage(CustomMessage('ExistingInstallSamePrompt'), ['{#MyAppName}', OldVersion, InstallPath]);
    SetArrayLength(BtnLabels, 3);
    BtnLabels[0] := CustomMessage('ExistingInstallBtnSetup');
    BtnLabels[1] := CustomMessage('ExistingInstallBtnUninstall');
    BtnLabels[2] := CustomMessage('ExistingInstallBtnCancel');

    MsgResult := SuppressibleTaskDialogMsgBox(
      CustomMessage('ExistingInstallSameHeading'),
      PromptMsg,
      mbConfirmation,
      MB_YESNOCANCEL,
      BtnLabels,
      0,
      IDYES
    );

    case MsgResult of
      IDYES:
      begin
        { Setup / Konfiguration erneut ausfuehren }
        Result := True;
      end;
      IDNO:
      begin
        { Deinstallation }
        Result := PerformUninstall(UninstallStr);
      end;
      IDCANCEL:
      begin
        { Vorgang abbrechen }
        Result := False;
      end;
    end;
    Exit;
  end;

  { Fall 3: Anwendung in niedrigerer Version installiert -> Angebot Update oder Deinstallation }
  if VerCmp < 0 then
  begin
    PromptMsg := FmtMessage(CustomMessage('ExistingInstallUpgradePrompt'), ['{#MyAppName}', OldVersion, InstallPath, '{#MyAppVersion}']);
    SetArrayLength(BtnLabels, 3);
    BtnLabels[0] := FmtMessage(CustomMessage('ExistingInstallBtnUpdate'), ['{#MyAppVersion}']);
    BtnLabels[1] := CustomMessage('ExistingInstallBtnUninstall');
    BtnLabels[2] := CustomMessage('ExistingInstallBtnCancel');

    MsgResult := SuppressibleTaskDialogMsgBox(
      CustomMessage('ExistingInstallUpgradeHeading'),
      PromptMsg,
      mbConfirmation,
      MB_YESNOCANCEL,
      BtnLabels,
      0,
      IDYES
    );

    case MsgResult of
      IDYES:
      begin
        { Update auf neue Version durchfuehren }
        Result := True;
      end;
      IDNO:
      begin
        { Deinstallation }
        Result := PerformUninstall(UninstallStr);
      end;
      IDCANCEL:
      begin
        { Vorgang abbrechen }
        Result := False;
      end;
    end;
    Exit;
  end;
end;

procedure InitializeWizard;
begin
  ConfigLoaded := False;

  { Schritt 1: Gemeinsame Auswahlseite fuer Schnittstellen (Datenbank & Webservice) }
  ServiceChoicePage := CreateCustomPage(wpSelectTasks, CustomMessage('ServiceChoicePageTitle'), CustomMessage('ServiceChoicePageSubTitle'));

  ServiceChoiceDescLabel := TLabel.Create(WizardForm);
  ServiceChoiceDescLabel.Parent := ServiceChoicePage.Surface;
  ServiceChoiceDescLabel.AutoSize := False;
  ServiceChoiceDescLabel.Left := ScaleX(0);
  ServiceChoiceDescLabel.Top := ScaleY(5);
  ServiceChoiceDescLabel.Width := ServiceChoicePage.SurfaceWidth;
  ServiceChoiceDescLabel.Height := ScaleY(35);
  ServiceChoiceDescLabel.WordWrap := True;
  ServiceChoiceDescLabel.Caption := CustomMessage('ServiceChoicePageDesc');

  DbEnableCheckBox := TNewCheckBox.Create(WizardForm);
  DbEnableCheckBox.Parent := ServiceChoicePage.Surface;
  DbEnableCheckBox.Left := ScaleX(0);
  DbEnableCheckBox.Top := ScaleY(45);
  DbEnableCheckBox.Width := ServiceChoicePage.SurfaceWidth;
  DbEnableCheckBox.Height := ScaleY(22);
  DbEnableCheckBox.Caption := CustomMessage('DbEnableCheck');
  DbEnableCheckBox.Checked := False;

  DbEnableHelpLabel := TLabel.Create(WizardForm);
  DbEnableHelpLabel.Parent := ServiceChoicePage.Surface;
  DbEnableHelpLabel.AutoSize := False;
  DbEnableHelpLabel.Left := ScaleX(20);
  DbEnableHelpLabel.Top := ScaleY(68);
  DbEnableHelpLabel.Width := ServiceChoicePage.SurfaceWidth - ScaleX(20);
  DbEnableHelpLabel.Height := ScaleY(30);
  DbEnableHelpLabel.WordWrap := True;
  DbEnableHelpLabel.Font.Color := clGrayText;
  DbEnableHelpLabel.Caption := CustomMessage('DbEnableHelp');

  WsEnableCheckBox := TNewCheckBox.Create(WizardForm);
  WsEnableCheckBox.Parent := ServiceChoicePage.Surface;
  WsEnableCheckBox.Left := ScaleX(0);
  WsEnableCheckBox.Top := ScaleY(105);
  WsEnableCheckBox.Width := ServiceChoicePage.SurfaceWidth;
  WsEnableCheckBox.Height := ScaleY(22);
  WsEnableCheckBox.Caption := CustomMessage('WsEnableCheck');
  WsEnableCheckBox.Checked := False;

  WsEnableHelpLabel := TLabel.Create(WizardForm);
  WsEnableHelpLabel.Parent := ServiceChoicePage.Surface;
  WsEnableHelpLabel.AutoSize := False;
  WsEnableHelpLabel.Left := ScaleX(20);
  WsEnableHelpLabel.Top := ScaleY(128);
  WsEnableHelpLabel.Width := ServiceChoicePage.SurfaceWidth - ScaleX(20);
  WsEnableHelpLabel.Height := ScaleY(30);
  WsEnableHelpLabel.WordWrap := True;
  WsEnableHelpLabel.Font.Color := clGrayText;
  WsEnableHelpLabel.Caption := CustomMessage('WsEnableHelp');

  { Schritt 2: DSN-Eingabe (wird nur angezeigt, wenn Datenbank aktiviert ist) }
  DbDsnPage := CreateCustomPage(ServiceChoicePage.ID, CustomMessage('DbDsnPageTitle'), CustomMessage('DbDsnPageSubTitle'));

  DbDsnDescLabel := TLabel.Create(WizardForm);
  DbDsnDescLabel.Parent := DbDsnPage.Surface;
  DbDsnDescLabel.AutoSize := False;
  DbDsnDescLabel.Left := ScaleX(0);
  DbDsnDescLabel.Top := ScaleY(5);
  DbDsnDescLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnDescLabel.Height := ScaleY(35);
  DbDsnDescLabel.WordWrap := True;
  DbDsnDescLabel.Caption := CustomMessage('DbDsnPageDesc');

  DbDsnLabel := TLabel.Create(WizardForm);
  DbDsnLabel.Parent := DbDsnPage.Surface;
  DbDsnLabel.AutoSize := False;
  DbDsnLabel.Left := ScaleX(0);
  DbDsnLabel.Top := ScaleY(45);
  DbDsnLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnLabel.Height := ScaleY(18);
  DbDsnLabel.Caption := CustomMessage('DbDsnLabel');

  DbDsnEdit := TNewEdit.Create(WizardForm);
  DbDsnEdit.Parent := DbDsnPage.Surface;
  DbDsnEdit.Left := ScaleX(0);
  DbDsnEdit.Top := ScaleY(65);
  DbDsnEdit.Width := DbDsnPage.SurfaceWidth;
  DbDsnEdit.Height := ScaleY(23);
  DbDsnEdit.Text := 'jdbc:mysql://localhost/rfid?user=rfid&password=XXX';

  DbDsnHelpLabel := TLabel.Create(WizardForm);
  DbDsnHelpLabel.Parent := DbDsnPage.Surface;
  DbDsnHelpLabel.AutoSize := False;
  DbDsnHelpLabel.Left := ScaleX(0);
  DbDsnHelpLabel.Top := ScaleY(92);
  DbDsnHelpLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnHelpLabel.Height := ScaleY(30);
  DbDsnHelpLabel.WordWrap := True;
  DbDsnHelpLabel.Font.Color := clGrayText;
  DbDsnHelpLabel.Caption := CustomMessage('DbDsnHelp');

  { Schritt 3: Webservice-Einstellungen (wird nur angezeigt, wenn Webservice aktiviert ist) }
  WsPage := CreateCustomPage(DbDsnPage.ID, CustomMessage('WsPageTitle'), CustomMessage('WsPageSubTitle'));

  WsDescLabel := TLabel.Create(WizardForm);
  WsDescLabel.Parent := WsPage.Surface;
  WsDescLabel.AutoSize := False;
  WsDescLabel.Left := ScaleX(0);
  WsDescLabel.Top := ScaleY(5);
  WsDescLabel.Width := WsPage.SurfaceWidth;
  WsDescLabel.Height := ScaleY(35);
  WsDescLabel.WordWrap := True;
  WsDescLabel.Caption := CustomMessage('WsPageDesc');

  WsTargetUrlLabel := TLabel.Create(WizardForm);
  WsTargetUrlLabel.Parent := WsPage.Surface;
  WsTargetUrlLabel.AutoSize := False;
  WsTargetUrlLabel.Left := ScaleX(0);
  WsTargetUrlLabel.Top := ScaleY(45);
  WsTargetUrlLabel.Width := WsPage.SurfaceWidth;
  WsTargetUrlLabel.Height := ScaleY(18);
  WsTargetUrlLabel.Caption := CustomMessage('WsTargetUrlLabel');

  WsTargetUrlEdit := TNewEdit.Create(WizardForm);
  WsTargetUrlEdit.Parent := WsPage.Surface;
  WsTargetUrlEdit.Left := ScaleX(0);
  WsTargetUrlEdit.Top := ScaleY(65);
  WsTargetUrlEdit.Width := WsPage.SurfaceWidth;
  WsTargetUrlEdit.Height := ScaleY(23);
  WsTargetUrlEdit.Text := 'https://httpbin.org/post';

  WsTargetUrlHelpLabel := TLabel.Create(WizardForm);
  WsTargetUrlHelpLabel.Parent := WsPage.Surface;
  WsTargetUrlHelpLabel.AutoSize := False;
  WsTargetUrlHelpLabel.Left := ScaleX(0);
  WsTargetUrlHelpLabel.Top := ScaleY(92);
  WsTargetUrlHelpLabel.Width := WsPage.SurfaceWidth;
  WsTargetUrlHelpLabel.Height := ScaleY(25);
  WsTargetUrlHelpLabel.WordWrap := True;
  WsTargetUrlHelpLabel.Font.Color := clGrayText;
  WsTargetUrlHelpLabel.Caption := CustomMessage('WsTargetUrlHelp');

  WsJwtKeyLabel := TLabel.Create(WizardForm);
  WsJwtKeyLabel.Parent := WsPage.Surface;
  WsJwtKeyLabel.AutoSize := False;
  WsJwtKeyLabel.Left := ScaleX(0);
  WsJwtKeyLabel.Top := ScaleY(122);
  WsJwtKeyLabel.Width := WsPage.SurfaceWidth;
  WsJwtKeyLabel.Height := ScaleY(18);
  WsJwtKeyLabel.Caption := CustomMessage('WsJwtKeyLabel');

  WsJwtKeyEdit := TNewEdit.Create(WizardForm);
  WsJwtKeyEdit.Parent := WsPage.Surface;
  WsJwtKeyEdit.Left := ScaleX(0);
  WsJwtKeyEdit.Top := ScaleY(142);
  WsJwtKeyEdit.Width := WsPage.SurfaceWidth;
  WsJwtKeyEdit.Height := ScaleY(23);
  WsJwtKeyEdit.Text := '';

  WsJwtKeyHelpLabel := TLabel.Create(WizardForm);
  WsJwtKeyHelpLabel.Parent := WsPage.Surface;
  WsJwtKeyHelpLabel.AutoSize := False;
  WsJwtKeyHelpLabel.Left := ScaleX(0);
  WsJwtKeyHelpLabel.Top := ScaleY(169);
  WsJwtKeyHelpLabel.Width := WsPage.SurfaceWidth;
  WsJwtKeyHelpLabel.Height := ScaleY(25);
  WsJwtKeyHelpLabel.WordWrap := True;
  WsJwtKeyHelpLabel.Font.Color := clGrayText;
  WsJwtKeyHelpLabel.Caption := CustomMessage('WsJwtKeyHelp');

  { Abschluss-Seite: Pfadanzeige fuer inventory.xml }
  FinishedConfigLabel := TLabel.Create(WizardForm);
  FinishedConfigLabel.Parent := WizardForm.FinishedPage;
  FinishedConfigLabel.AutoSize := False;
  FinishedConfigLabel.Left := WizardForm.FinishedLabel.Left;
  FinishedConfigLabel.Top := ScaleY(120);
  FinishedConfigLabel.Width := WizardForm.FinishedLabel.Width;
  FinishedConfigLabel.Height := ScaleY(18);
  FinishedConfigLabel.Caption := CustomMessage('FinishedConfigInfo');

  FinishedConfigEdit := TNewEdit.Create(WizardForm);
  FinishedConfigEdit.Parent := WizardForm.FinishedPage;
  FinishedConfigEdit.Left := WizardForm.FinishedLabel.Left;
  FinishedConfigEdit.Top := ScaleY(140);
  FinishedConfigEdit.Width := WizardForm.FinishedLabel.Width;
  FinishedConfigEdit.Height := ScaleY(23);
  FinishedConfigEdit.ReadOnly := True;

  { Positionierung der RunList (Launch Checkbox) anpassen, falls vorhanden }
  if WizardForm.RunList <> nil then
  begin
    WizardForm.RunList.Top := ScaleY(175);
  end;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  { Wenn die Datenbank nicht aktiviert wurde, ueberspringe die DSN-Eingabeseite }
  if (DbDsnPage <> nil) and (PageID = DbDsnPage.ID) then
  begin
    if not DbEnableCheckBox.Checked then
      Result := True;
  end;

  { Wenn der Webservice nicht aktiviert wurde, ueberspringe die Webservice-Eingabeseite }
  if (WsPage <> nil) and (PageID = WsPage.ID) then
  begin
    if not WsEnableCheckBox.Checked then
      Result := True;
  end;
end;

procedure LoadExistingConfig;
var
  ConfigFile: String;
  Lines: TArrayOfString;
  I, P1, P2: Integer;
  InDbBlock, InWsBlock: Boolean;
  LineStr: String;
begin
  if ConfigLoaded then Exit;
  ConfigFile := ExpandConstant('{app}\inventory.xml');
  if FileExists(ConfigFile) and LoadStringsFromFile(ConfigFile, Lines) then
  begin
    InDbBlock := False;
    InWsBlock := False;
    for I := 0 to GetArrayLength(Lines) - 1 do
    begin
      if Pos('<database>', Lines[I]) > 0 then
        InDbBlock := True
      else if Pos('</database>', Lines[I]) > 0 then
        InDbBlock := False;

      if Pos('<webservice>', Lines[I]) > 0 then
        InWsBlock := True
      else if Pos('</webservice>', Lines[I]) > 0 then
        InWsBlock := False;

      if InDbBlock then
      begin
        if Pos('<active>true</active>', LowerCase(Lines[I])) > 0 then
          DbEnableCheckBox.Checked := True;
        if Pos('<active>false</active>', LowerCase(Lines[I])) > 0 then
          DbEnableCheckBox.Checked := False;

        P1 := Pos('<dsn><![CDATA[', Lines[I]);
        if P1 > 0 then
        begin
          LineStr := Copy(Lines[I], P1 + 13, Length(Lines[I]));
          P2 := Pos(']]></dsn>', LineStr);
          if P2 > 0 then
            DbDsnEdit.Text := Copy(LineStr, 1, P2 - 1);
        end
        else
        begin
          P1 := Pos('<dsn>', Lines[I]);
          if P1 > 0 then
          begin
            LineStr := Copy(Lines[I], P1 + 5, Length(Lines[I]));
            P2 := Pos('</dsn>', LineStr);
            if P2 > 0 then
              DbDsnEdit.Text := Copy(LineStr, 1, P2 - 1);
          end;
        end;
      end;

      if InWsBlock then
      begin
        if Pos('<active>true</active>', LowerCase(Lines[I])) > 0 then
          WsEnableCheckBox.Checked := True;
        if Pos('<active>false</active>', LowerCase(Lines[I])) > 0 then
          WsEnableCheckBox.Checked := False;

        P1 := Pos('<target_url><![CDATA[', Lines[I]);
        if P1 > 0 then
        begin
          LineStr := Copy(Lines[I], P1 + 21, Length(Lines[I]));
          P2 := Pos(']]></target_url>', LineStr);
          if P2 > 0 then
            WsTargetUrlEdit.Text := Copy(LineStr, 1, P2 - 1);
        end
        else
        begin
          P1 := Pos('<target_url>', Lines[I]);
          if P1 > 0 then
          begin
            LineStr := Copy(Lines[I], P1 + 12, Length(Lines[I]));
            P2 := Pos('</target_url>', LineStr);
            if P2 > 0 then
              WsTargetUrlEdit.Text := Copy(LineStr, 1, P2 - 1);
          end;
        end;

        P1 := Pos('<jwt_key><![CDATA[', Lines[I]);
        if P1 > 0 then
        begin
          LineStr := Copy(Lines[I], P1 + 18, Length(Lines[I]));
          P2 := Pos(']]></jwt_key>', LineStr);
          if P2 > 0 then
            WsJwtKeyEdit.Text := Copy(LineStr, 1, P2 - 1);
        end
        else
        begin
          P1 := Pos('<jwt_key>', Lines[I]);
          if P1 > 0 then
          begin
            LineStr := Copy(Lines[I], P1 + 9, Length(Lines[I]));
            P2 := Pos('</jwt_key>', LineStr);
            if P2 > 0 then
              WsJwtKeyEdit.Text := Copy(LineStr, 1, P2 - 1);
          end;
        end;
      end;
    end;
    ConfigLoaded := True;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if ((ServiceChoicePage <> nil) and (CurPageID = ServiceChoicePage.ID)) or
     ((DbDsnPage <> nil) and (CurPageID = DbDsnPage.ID)) or
     ((WsPage <> nil) and (CurPageID = WsPage.ID)) then
  begin
    LoadExistingConfig;
  end;

  if CurPageID = wpFinished then
  begin
    FinishedConfigEdit.Text := ExpandConstant('{app}\inventory.xml');
  end;
end;

procedure UpdateInventoryConfig;
var
  ConfigFile: String;
  Lines: TArrayOfString;
  I: Integer;
  DbActiveVal: String;
  WsActiveVal: String;
  DsnVal: String;
  TargetUrlVal: String;
  JwtKeyVal: String;
  InDbBlock, InWsBlock: Boolean;
begin
  ConfigFile := ExpandConstant('{app}\inventory.xml');
  if not FileExists(ConfigFile) then Exit;

  if DbEnableCheckBox.Checked then
    DbActiveVal := 'true'
  else
    DbActiveVal := 'false';

  if WsEnableCheckBox.Checked then
    WsActiveVal := 'true'
  else
    WsActiveVal := 'false';

  DsnVal := DbDsnEdit.Text;
  TargetUrlVal := WsTargetUrlEdit.Text;
  JwtKeyVal := WsJwtKeyEdit.Text;

  if LoadStringsFromFile(ConfigFile, Lines) then
  begin
    InDbBlock := False;
    InWsBlock := False;
    for I := 0 to GetArrayLength(Lines) - 1 do
    begin
      if Pos('<database>', Lines[I]) > 0 then
        InDbBlock := True
      else if Pos('</database>', Lines[I]) > 0 then
        InDbBlock := False;

      if Pos('<webservice>', Lines[I]) > 0 then
        InWsBlock := True
      else if Pos('</webservice>', Lines[I]) > 0 then
        InWsBlock := False;

      if InDbBlock then
      begin
        if Pos('<active>', Lines[I]) > 0 then
          Lines[I] := #9#9'<active>' + DbActiveVal + '</active>';
        if Pos('<dsn>', Lines[I]) > 0 then
          Lines[I] := #9#9'<dsn><![CDATA[' + DsnVal + ']]></dsn>';
      end;

      if InWsBlock then
      begin
        if Pos('<active>', Lines[I]) > 0 then
          Lines[I] := #9#9'<active>' + WsActiveVal + '</active>';
        if Pos('<target_url>', Lines[I]) > 0 then
          Lines[I] := #9#9'<target_url><![CDATA[' + TargetUrlVal + ']]></target_url>';
        if Pos('<jwt_key>', Lines[I]) > 0 then
          Lines[I] := #9#9'<jwt_key><![CDATA[' + JwtKeyVal + ']]></jwt_key>';
      end;
    end;
    SaveStringsToFile(ConfigFile, Lines, False);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    UpdateInventoryConfig;
  end;
end;

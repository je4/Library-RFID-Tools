; Script generated for Inno Setup 6
; RFID Inventory - info-age GmbH, Basel

#define MyAppName "RFID Inventory"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "info-age GmbH, Basel"
#define MyAppExeName "RFID-Inventory.exe"

[Setup]
AppId={{9C8B5F2A-4D71-4A5B-A28E-9872C1579D3B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\RFID-Inventory
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=RFID-Inventory-Setup-{#MyAppVersion}
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
german.DbChoicePageTitle=Datenbank-Nutzung
german.DbChoicePageSubTitle=Möchten Sie eine relationale SQL-Datenbank zur Protokollierung verwenden?
german.DbChoicePageDesc=Falls Sie Inventarisierungs- und Mediendaten in einer SQL-Datenbank (MySQL / MariaDB) speichern möchten, können Sie die Datenbankanbindung hier aktivieren.
german.DbEnableCheck=Datenbankanbindung aktivieren

german.DbDsnPageTitle=Datenbank-Verbindung
german.DbDsnPageSubTitle=Geben Sie die Verbindungszeichenfolge (DSN) für die SQL-Datenbank an.
german.DbDsnPageDesc=Geben Sie die JDBC-Verbindungszeichenfolge ein, über die sich die Anwendung mit Ihrer Datenbank verbindet:
german.DbDsnLabel=JDBC-Datenbank-DSN:
german.DbDsnHelp=Beispiel: jdbc:mysql://localhost/rfid?user=rfid&password=XXX

german.FinishedConfigInfo=Pfad zur Konfigurationsdatei (inventory.xml):

english.DbChoicePageTitle=Database Usage
english.DbChoicePageSubTitle=Do you want to use a relational SQL database for logging?
english.DbChoicePageDesc=If you wish to log inventory and tag data to a SQL database (MySQL / MariaDB), you can enable the database connection here.
english.DbEnableCheck=Enable database connection

english.DbDsnPageTitle=Database Connection
english.DbDsnPageSubTitle=Specify the connection string (DSN) for the SQL database.
english.DbDsnPageDesc=Enter the JDBC connection string used to connect to your database:
english.DbDsnLabel=JDBC Database DSN:
english.DbDsnHelp=Example: jdbc:mysql://localhost/rfid?user=rfid&password=XXX

english.FinishedConfigInfo=Path to configuration file (inventory.xml):

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Main application image and files
Source: "target\dist\RFID-Inventory\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Configuration file (do not overwrite if modified by user)
Source: "inventory.xml"; DestDir: "{app}"; Flags: onlyifdoesntexist

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  DbChoicePage: TWizardPage;
  DbChoiceDescLabel: TLabel;
  DbEnableCheckBox: TNewCheckBox;

  DbDsnPage: TWizardPage;
  DbDsnDescLabel: TLabel;
  DbDsnLabel: TLabel;
  DbDsnEdit: TNewEdit;
  DbDsnHelpLabel: TLabel;

  FinishedConfigLabel: TLabel;
  FinishedConfigEdit: TNewEdit;
  ConfigLoaded: Boolean;

procedure InitializeWizard;
begin
  ConfigLoaded := False;

  { Schritt 1: Datenbank-Aktivierung }
  DbChoicePage := CreateCustomPage(wpSelectTasks, CustomMessage('DbChoicePageTitle'), CustomMessage('DbChoicePageSubTitle'));

  DbChoiceDescLabel := TLabel.Create(WizardForm);
  DbChoiceDescLabel.Parent := DbChoicePage.Surface;
  DbChoiceDescLabel.Top := ScaleY(5);
  DbChoiceDescLabel.Left := ScaleX(0);
  DbChoiceDescLabel.Width := DbChoicePage.SurfaceWidth;
  DbChoiceDescLabel.WordWrap := True;
  DbChoiceDescLabel.Caption := CustomMessage('DbChoicePageDesc');

  DbEnableCheckBox := TNewCheckBox.Create(WizardForm);
  DbEnableCheckBox.Parent := DbChoicePage.Surface;
  DbEnableCheckBox.Top := ScaleY(55);
  DbEnableCheckBox.Left := ScaleX(0);
  DbEnableCheckBox.Width := DbChoicePage.SurfaceWidth;
  DbEnableCheckBox.Height := ScaleY(22);
  DbEnableCheckBox.Caption := CustomMessage('DbEnableCheck');
  DbEnableCheckBox.Checked := False;

  { Schritt 2: DSN-Eingabe (wird nur angezeigt, wenn Datenbank aktiviert ist) }
  DbDsnPage := CreateCustomPage(DbChoicePage.ID, CustomMessage('DbDsnPageTitle'), CustomMessage('DbDsnPageSubTitle'));

  DbDsnDescLabel := TLabel.Create(WizardForm);
  DbDsnDescLabel.Parent := DbDsnPage.Surface;
  DbDsnDescLabel.Top := ScaleY(5);
  DbDsnDescLabel.Left := ScaleX(0);
  DbDsnDescLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnDescLabel.WordWrap := True;
  DbDsnDescLabel.Caption := CustomMessage('DbDsnPageDesc');

  DbDsnLabel := TLabel.Create(WizardForm);
  DbDsnLabel.Parent := DbDsnPage.Surface;
  DbDsnLabel.Top := ScaleY(45);
  DbDsnLabel.Left := ScaleX(0);
  DbDsnLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnLabel.Caption := CustomMessage('DbDsnLabel');

  DbDsnEdit := TNewEdit.Create(WizardForm);
  DbDsnEdit.Parent := DbDsnPage.Surface;
  DbDsnEdit.Top := ScaleY(65);
  DbDsnEdit.Left := ScaleX(0);
  DbDsnEdit.Width := DbDsnPage.SurfaceWidth;
  DbDsnEdit.Text := 'jdbc:mysql://localhost/rfid?user=rfid&password=XXX';

  DbDsnHelpLabel := TLabel.Create(WizardForm);
  DbDsnHelpLabel.Parent := DbDsnPage.Surface;
  DbDsnHelpLabel.Top := ScaleY(95);
  DbDsnHelpLabel.Left := ScaleX(0);
  DbDsnHelpLabel.Width := DbDsnPage.SurfaceWidth;
  DbDsnHelpLabel.WordWrap := True;
  DbDsnHelpLabel.Font.Color := clGrayText;
  DbDsnHelpLabel.Caption := CustomMessage('DbDsnHelp');

  { Abschluss-Seite: Pfadanzeige für inventory.xml }
  FinishedConfigLabel := TLabel.Create(WizardForm);
  FinishedConfigLabel.Parent := WizardForm.FinishedPage;
  FinishedConfigLabel.Top := ScaleY(120);
  FinishedConfigLabel.Left := WizardForm.FinishedLabel.Left;
  FinishedConfigLabel.Width := WizardForm.FinishedLabel.Width;
  FinishedConfigLabel.Caption := CustomMessage('FinishedConfigInfo');

  FinishedConfigEdit := TNewEdit.Create(WizardForm);
  FinishedConfigEdit.Parent := WizardForm.FinishedPage;
  FinishedConfigEdit.Top := ScaleY(140);
  FinishedConfigEdit.Left := WizardForm.FinishedLabel.Left;
  FinishedConfigEdit.Width := WizardForm.FinishedLabel.Width;
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
  { Wenn die Datenbank nicht aktiviert wurde, überspringe die DSN-Eingabeseite }
  if (DbDsnPage <> nil) and (PageID = DbDsnPage.ID) then
  begin
    if not DbEnableCheckBox.Checked then
      Result := True;
  end;
end;

procedure LoadExistingConfig;
var
  ConfigFile: String;
  Lines: TArrayOfString;
  I, P1, P2: Integer;
  InDbBlock: Boolean;
  LineStr: String;
begin
  if ConfigLoaded then Exit;
  ConfigFile := ExpandConstant('{app}\inventory.xml');
  if FileExists(ConfigFile) and LoadStringsFromFile(ConfigFile, Lines) then
  begin
    InDbBlock := False;
    for I := 0 to GetArrayLength(Lines) - 1 do
    begin
      if Pos('<database>', Lines[I]) > 0 then
        InDbBlock := True
      else if Pos('</database>', Lines[I]) > 0 then
        InDbBlock := False;

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
        end;
      end;
    end;
    ConfigLoaded := True;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (DbChoicePage <> nil) and (CurPageID = DbChoicePage.ID) then
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
  ActiveVal: String;
  DsnVal: String;
  InDbBlock: Boolean;
begin
  ConfigFile := ExpandConstant('{app}\inventory.xml');
  if not FileExists(ConfigFile) then Exit;

  if DbEnableCheckBox.Checked then
    ActiveVal := 'true'
  else
    ActiveVal := 'false';

  DsnVal := DbDsnEdit.Text;

  if LoadStringsFromFile(ConfigFile, Lines) then
  begin
    InDbBlock := False;
    for I := 0 to GetArrayLength(Lines) - 1 do
    begin
      if Pos('<database>', Lines[I]) > 0 then
        InDbBlock := True
      else if Pos('</database>', Lines[I]) > 0 then
        InDbBlock := False;

      if InDbBlock then
      begin
        if Pos('<active>', Lines[I]) > 0 then
          Lines[I] := #9#9'<active>' + ActiveVal + '</active>';
        if Pos('<dsn>', Lines[I]) > 0 then
          Lines[I] := #9#9'<dsn><![CDATA[' + DsnVal + ']]></dsn>';
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

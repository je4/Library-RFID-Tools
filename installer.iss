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

#include "installer-messages.iss"

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
#include "scripts\version-check.pas"
#include "scripts\wizard-pages.pas"
#include "scripts\xml-config.pas"

function InitializeSetup(): Boolean;
begin
  Result := HandleExistingInstallation();
end;

procedure InitializeWizard;
begin
  CreateCustomConfigPages();
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := ShouldSkipConfigPage(PageID);
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

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    UpdateInventoryConfig;
  end;
end;

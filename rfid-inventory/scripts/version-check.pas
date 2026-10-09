{ ========================================================
  RFID Inventory - Version & Upgrade Management
  info-age GmbH, Basel
  ======================================================== }

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

function HandleExistingInstallation(): Boolean;
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

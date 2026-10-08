{ ========================================================
  RFID Inventory - Wizard Pages & UI Configuration
  info-age GmbH, Basel
  ======================================================== }

var
  UiChoicePage: TWizardPage;
  UiChoiceDescLabel: TLabel;
  UiModernRadio: TNewRadioButton;
  UiModernHelpLabel: TLabel;
  UiSwtRadio: TNewRadioButton;
  UiSwtHelpLabel: TLabel;

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

procedure CreateCustomConfigPages;
begin
  ConfigLoaded := False;

  { Schritt 1: Benutzeroberflaeche auswaehlen (Modern FlatLaf vs. Klassisch SWT) }
  UiChoicePage := CreateCustomPage(wpSelectTasks, CustomMessage('UiChoicePageTitle'), CustomMessage('UiChoicePageSubTitle'));

  UiChoiceDescLabel := TLabel.Create(WizardForm);
  UiChoiceDescLabel.Parent := UiChoicePage.Surface;
  UiChoiceDescLabel.AutoSize := False;
  UiChoiceDescLabel.Left := ScaleX(0);
  UiChoiceDescLabel.Top := ScaleY(5);
  UiChoiceDescLabel.Width := UiChoicePage.SurfaceWidth;
  UiChoiceDescLabel.Height := ScaleY(35);
  UiChoiceDescLabel.WordWrap := True;
  UiChoiceDescLabel.Caption := CustomMessage('UiChoicePageDesc');

  UiModernRadio := TNewRadioButton.Create(WizardForm);
  UiModernRadio.Parent := UiChoicePage.Surface;
  UiModernRadio.Left := ScaleX(0);
  UiModernRadio.Top := ScaleY(45);
  UiModernRadio.Width := UiChoicePage.SurfaceWidth;
  UiModernRadio.Height := ScaleY(22);
  UiModernRadio.Caption := CustomMessage('UiModernRadio');
  UiModernRadio.Checked := True;

  UiModernHelpLabel := TLabel.Create(WizardForm);
  UiModernHelpLabel.Parent := UiChoicePage.Surface;
  UiModernHelpLabel.AutoSize := False;
  UiModernHelpLabel.Left := ScaleX(20);
  UiModernHelpLabel.Top := ScaleY(68);
  UiModernHelpLabel.Width := UiChoicePage.SurfaceWidth - ScaleX(20);
  UiModernHelpLabel.Height := ScaleY(30);
  UiModernHelpLabel.WordWrap := True;
  UiModernHelpLabel.Font.Color := clGrayText;
  UiModernHelpLabel.Caption := CustomMessage('UiModernHelp');

  UiSwtRadio := TNewRadioButton.Create(WizardForm);
  UiSwtRadio.Parent := UiChoicePage.Surface;
  UiSwtRadio.Left := ScaleX(0);
  UiSwtRadio.Top := ScaleY(105);
  UiSwtRadio.Width := UiChoicePage.SurfaceWidth;
  UiSwtRadio.Height := ScaleY(22);
  UiSwtRadio.Caption := CustomMessage('UiSwtRadio');
  UiSwtRadio.Checked := False;

  UiSwtHelpLabel := TLabel.Create(WizardForm);
  UiSwtHelpLabel.Parent := UiChoicePage.Surface;
  UiSwtHelpLabel.AutoSize := False;
  UiSwtHelpLabel.Left := ScaleX(20);
  UiSwtHelpLabel.Top := ScaleY(128);
  UiSwtHelpLabel.Width := UiChoicePage.SurfaceWidth - ScaleX(20);
  UiSwtHelpLabel.Height := ScaleY(30);
  UiSwtHelpLabel.WordWrap := True;
  UiSwtHelpLabel.Font.Color := clGrayText;
  UiSwtHelpLabel.Caption := CustomMessage('UiSwtHelp');

  { Schritt 2: Gemeinsame Auswahlseite fuer Schnittstellen (Datenbank & Webservice) }
  ServiceChoicePage := CreateCustomPage(UiChoicePage.ID, CustomMessage('ServiceChoicePageTitle'), CustomMessage('ServiceChoicePageSubTitle'));

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

function ShouldSkipConfigPage(PageID: Integer): Boolean;
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

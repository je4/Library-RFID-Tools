{ ========================================================
  RFID Inventory - XML Configuration Management
  info-age GmbH, Basel
  ======================================================== }

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

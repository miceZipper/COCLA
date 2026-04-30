; COCLA Installer Script
; Supports Typical and Custom installation

#define AppName "COCLA"
#define AppVersion "1.2"
#define AppPublisher "miceZipper"
#define AppURL "https://github.com/miceZipper/COCLA"
#define DefaultMariaDBPort "3306"
#define DefaultGrafanaPort "3000"

[Setup]
AppId={{B8F4A2D1-9C5E-4A7F-B6D8-1E3F5A7C9B2D}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
AllowNoIcons=yes
LicenseFile=LICENSE.txt
OutputDir=output
OutputBaseFilename=COCLA-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ChangesEnvironment=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Types]
Name: "typical"; Description: "Typical installation - All components with default settings"
Name: "custom"; Description: "Custom installation - Choose components and configure manually"; Flags: iscustom

[Components]
Name: "java"; Description: "OpenJDK 26 (Portable)"; Types: typical custom
Name: "mariadb"; Description: "MariaDB 12.2.2"; Types: typical custom
Name: "grafana"; Description: "Grafana 13.0.1"; Types: typical custom
Name: "cocla"; Description: "COCLA Core Application"; Types: typical custom; Flags: fixed

[Files]
; COCLA
Source: "cocla.jar"; DestDir: "{app}"; Flags: ignoreversion; Components: cocla
Source: "config\cocla\schema.sql"; DestDir: "{app}\config\cocla"; Flags: ignoreversion; Components: mariadb cocla

; Grafana provisioning
Source: "config\grafana\provisioning\datasources\mysql-cocla.yml"; DestDir: "{app}\config\grafana\provisioning\datasources"; Flags: ignoreversion; Components: grafana
Source: "config\grafana\provisioning\dashboards\cocla.yml"; DestDir: "{app}\config\grafana\provisioning\dashboards"; Flags: ignoreversion; Components: grafana
Source: "config\grafana\provisioning\dashboards\cocla\cocla-dashboard.json"; DestDir: "{app}\config\grafana\provisioning\dashboards\cocla"; Flags: ignoreversion; Components: grafana

; Redistributables
Source: "redist\win64\openjdk-26_windows-x64_bin.zip"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: java
Source: "redist\win64\mariadb-12.2.2-winx64.msi"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: mariadb
Source: "redist\win64\grafana_13.0.1_24542347077_windows_amd64.msi"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: grafana

[Icons]
Name: "{group}\Start COCLA"; Filename: "{app}\start.bat"; WorkingDir: "{app}"
Name: "{group}\Stop COCLA"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"
Name: "{group}\COCLA Dashboard"; Filename: "http://localhost:{code:GetGrafanaPort}"; Components: grafana
Name: "{group}\Uninstall COCLA"; Filename: "{uninstallexe}"

[Code]
var
  CustomPage: TInputQueryWizardPage;
  MariaDBPort, GrafanaPort: String;
  MariaDBPassword, GrafanaPassword, GrafanaDBPassword, CoclaDBPassword: String;
  MariaDBDataDir, GrafanaHomeDir: String;
  InstallMariaDB, InstallGrafana, InstallJava: Boolean;
  GameLogDir: String;

// Получить порт Grafana для иконки
function GetGrafanaPort(Param: String): String;
begin
  Result := GrafanaPort;
end;

// Проверка свободен ли порт
function IsPortAvailable(Port: Integer): Boolean;
var
  ResultCode: Integer;
begin
  Exec('netstat', '-an | find ":' + IntToStr(Port) + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Result := (ResultCode <> 0);
end;

// Найти следующий свободный порт
function FindNextAvailablePort(StartPort: Integer): Integer;
begin
  Result := StartPort;
  while not IsPortAvailable(Result) do
    Result := Result + 1;
end;

procedure InitializeWizard;
begin
  MariaDBPort := '{#DefaultMariaDBPort}';
  GrafanaPort := '{#DefaultGrafanaPort}';
  MariaDBPassword := 'root';
  GrafanaPassword := 'grafana';
  GrafanaDBPassword := 'grafana';
  CoclaDBPassword := 'cocla';
  GameLogDir := 'C:\Program Files (x86)\Steam\steamapps\common\Champions Online\Champions Online\Live\logs\Client';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  
  if CurPageID = wpSelectComponents then
  begin
    InstallJava := IsComponentSelected('java');
    InstallMariaDB := IsComponentSelected('mariadb');
    InstallGrafana := IsComponentSelected('grafana');
    
    // Если custom, показываем страницу настроек
    if WizardIsComponentSelected('custom') then
    begin
      CustomPage := CreateInputQueryPage(wpSelectComponents,
        'Custom Configuration', 'Configure COCLA components',
        'Please specify the configuration parameters.You can leave defaults if unsure.');
      
      if InstallMariaDB then
      begin
        CustomPage.Add('MariaDB Port:', False);
        CustomPage.Add('MariaDB Root Password:', False);
        CustomPage.Add('MariaDB Data Directory:', False);
      end;
      
      if InstallGrafana then
      begin
        CustomPage.Add('Grafana Port:', False);
        CustomPage.Add('Grafana Admin Password:', False);
      end;
      
      CustomPage.Add('Game Log Directory:', False);
      
      // Заполняем дефолтные значения
      if InstallMariaDB then
      begin
        CustomPage.Values[0] := MariaDBPort;
        CustomPage.Values[1] := MariaDBPassword;
        CustomPage.Values[2] := ExpandConstant('{app}\mariadb\data');
      end;
      
      if InstallGrafana then
      begin
        CustomPage.Values[3] := GrafanaPort;
        CustomPage.Values[4] := GrafanaPassword;
      end;
      
      CustomPage.Values[5] := GameLogDir;
    end;
  end;
  
  // После custom page, валидируем
  if (CurPageID = CustomPage.ID) and WizardIsComponentSelected('custom') then
  begin
    if InstallMariaDB then
    begin
      MariaDBPort := CustomPage.Values[0];
      MariaDBPassword := CustomPage.Values[1];
      MariaDBDataDir := CustomPage.Values[2];
    end;
    
    if InstallGrafana then
    begin
      GrafanaPort := CustomPage.Values[3];
      GrafanaPassword := CustomPage.Values[4];
    end;
    
    GameLogDir := CustomPage.Values[5];
    
    // Проверка существования папки логов
    if not DirExists(GameLogDir) then
    begin
      if WizardIsComponentSelected('typical') then
      begin
        if BrowseForFolder('Game log directory not found! Please locate it:', GameLogDir, False) then
        begin
          // ок
        end
        else
        begin
          MsgBox('Game log directory must be specified for COCLA to work.', mbError, MB_OK);
          Result := False;
        end;
      end
      else
      begin
        MsgBox('Game log directory does not exist: ' + GameLogDir + #13#10 +
               'Please create it first or specify an existing directory.', mbError, MB_OK);
        Result := False;
      end;
    end;
  end;
end;

// Установка MariaDB (тихо, без службы)
function InstallMariaDBSilent: Boolean;
var
  ResultCode: Integer;
  MSIPath, DataDir, InstallDir: String;
begin
  Result := False;
  MSIPath := ExpandConstant('{tmp}\mariadb-12.2.2-winx64.msi');
  InstallDir := ExpandConstant('{app}\mariadb');
  DataDir := ExpandConstant('{app}\mariadb\data');
  
  // Тихая установка БЕЗ службы
  if Exec('msiexec', '/i "' + MSIPath + '" /quiet /norestart ' +
     'INSTALLDIR="' + InstallDir + '" ' +
     'DATADIR="' + DataDir + '" ' +
     'SERVICENAME="" ' +                    // Не устанавливать службу!
     'PORT=' + MariaDBPort + ' ' +
     'PASSWORD="' + MariaDBPassword + '" ' +
     'ALLOWEMPTYPASSWORD=0 ' +
     'SKIPPERMISSIONSFIX=1',
     '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    Sleep(3000);
    Result := (ResultCode = 0) or (ResultCode = 1641); // 1641 = reboot initiated (но не нужно)
  end;
end;

// Установка Grafana (тихо, без службы)
function InstallGrafanaSilent: Boolean;
var
  ResultCode: Integer;
  MSIPath, InstallDir: String;
begin
  Result := False;
  MSIPath := ExpandConstant('{tmp}\grafana_13.0.1_24542347077_windows_amd64.msi');
  InstallDir := ExpandConstant('{app}\grafana');
  
  // Тихая установка БЕЗ службы
  if Exec('msiexec', '/i "' + MSIPath + '" /quiet /norestart ' +
     'INSTALLDIR="' + InstallDir + '" ' +
     'REGISTERSERVICE=false',
     '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    Sleep(3000);
    Result := (ResultCode = 0) or (ResultCode = 1641);
  end;
end;

// Извлечение OpenJDK
function ExtractOpenJDK: Boolean;
var
  ZipFile, ExtractPath: String;
  FindRec: TFindRec;
  JavaHome: String;
begin
  Result := False;
  ZipFile := ExpandConstant('{tmp}\openjdk-26_windows-x64_bin.zip');
  ExtractPath := ExpandConstant('{app}\java');
  
  if Unzip(ZipFile, ExtractPath) then
  begin
    // Найти папку с JDK внутри
    if FindFirst(ExtractPath + '\*', FindRec) then
    begin
      repeat
        if (FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY) <> 0 then
        begin
          if Pos('jdk', LowerCase(FindRec.Name)) > 0 then
          begin
            JavaHome := ExtractPath + '\' + FindRec.Name;
            Result := True;
            Break;
          end;
        end;
      until not FindNext(FindRec);
      FindClose(FindRec);
    end;
  end;
  
  if Result then
    Log('OpenJDK extracted to: ' + JavaHome)
  else
    Log('ERROR: Failed to extract OpenJDK');
end;

// Unzip helper
function Unzip(ZipFile, DestDir: String): Boolean;
var
  Shell: Variant;
  ZipFolder: Variant;
begin
  Result := False;
  try
    Shell := CreateOleObject('Shell.Application');
    ZipFolder := Shell.NameSpace(ZipFile);
    if not VarIsClear(ZipFolder) then
    begin
      Shell.NameSpace(DestDir).CopyHere(ZipFolder.Items, 4 or 16);
      Sleep(2000);
      Result := True;
    end;
  except
    Result := False;
  end;
end;

// Выполнить schema.sql
procedure ExecuteSchemaSQL;
var
  SQLPath, MysqlExe: String;
  ResultCode: Integer;
begin
  SQLPath := ExpandConstant('{app}\config\cocla\schema.sql');
  MysqlExe := ExpandConstant('{app}\mariadb\bin\mysql.exe');
  
  if FileExists(SQLPath) and FileExists(MysqlExe) then
  begin
    Exec(MysqlExe, '-u root -p' + MariaDBPassword + ' -P ' + MariaDBPort + ' < "' + SQLPath + '"',
         '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    if ResultCode = 0 then
      Log('Schema.sql executed successfully')
    else
      Log('WARNING: schema.sql execution returned code: ' + IntToStr(ResultCode));
  end
  else
    Log('ERROR: schema.sql or mysql.exe not found');
end;

// Настройка Grafana provisioning
procedure ConfigureGrafanaProvisioning;
var
  IniFile, ProvisioningDir, SourceFile, DestFile: String;
begin
  // Копируем custom.ini
  SourceFile := ExpandConstant('{app}\grafana\conf\sample.ini');
  DestFile := ExpandConstant('{app}\grafana\conf\custom.ini');
  if FileExists(SourceFile) and not FileExists(DestFile) then
    FileCopy(SourceFile, DestFile, False);
  
  IniFile := ExpandConstant('{app}\grafana\conf\custom.ini');
  
  // Записываем настройки в custom.ini
  SetIniString('server', 'http_port', GrafanaPort, IniFile);
  SetIniString('database', 'type', 'mysql', IniFile);
  SetIniString('database', 'host', '127.0.0.1:' + MariaDBPort, IniFile);
  SetIniString('database', 'name', 'grafana', IniFile);
  SetIniString('database', 'user', 'grafana', IniFile);
  SetIniString('database', 'password', GrafanaDBPassword, IniFile);
  SetIniString('security', 'admin_user', 'admin', IniFile);
  SetIniString('security', 'admin_password', GrafanaPassword, IniFile);
  SetIniString('users', 'allow_sign_up', 'false', IniFile);
  SetIniString('users', 'allow_org_create', 'false', IniFile);
  
  // Provisioning paths
  SetIniString('paths', 'provisioning', 
               ExpandConstant('{app}\config\grafana\provisioning'), IniFile);
  
  Log('Grafana custom.ini configured');
end;

// Создать start.bat
procedure CreateStartBat;
var
  BatFile: TStringList;
  BatPath: String;
begin
  BatPath := ExpandConstant('{app}\start.bat');
  BatFile := TStringList.Create;
  try
    BatFile.Add('@echo off');
    BatFile.Add('title COCLA v{#AppVersion}');
    BatFile.Add('');
    BatFile.Add('set COCLA_HOME=%~dp0');
    BatFile.Add('');
    
    // Найти свободные порты
    BatFile.Add('set MARIADB_PORT=' + MariaDBPort);
    BatFile.Add('set GRAFANA_PORT=' + GrafanaPort);
    BatFile.Add('');
    BatFile.Add(':check_mariadb_port');
    BatFile.Add('netstat -an | find ":%MARIADB_PORT%" >nul');
    BatFile.Add('if %errorlevel% EQU 0 (');
    BatFile.Add('    set /a MARIADB_PORT+=1');
    BatFile.Add('    echo MariaDB port in use, trying %MARIADB_PORT%');
    BatFile.Add('    goto check_mariadb_port');
    BatFile.Add(')');
    BatFile.Add('');
    BatFile.Add(':check_grafana_port');
    BatFile.Add('netstat -an | find ":%GRAFANA_PORT%" >nul');
    BatFile.Add('if %errorlevel% EQU 0 (');
    BatFile.Add('    set /a GRAFANA_PORT+=1');
    BatFile.Add('    echo Grafana port in use, trying %GRAFANA_PORT%');
    BatFile.Add('    goto check_grafana_port');
    BatFile.Add(')');
    BatFile.Add('');
    
    // Запуск MariaDB
    BatFile.Add('echo Starting MariaDB...');
    BatFile.Add('start "MariaDB" /MIN "%%COCLA_HOME%%mariadb\bin\mysqld" --datadir="%%COCLA_HOME%%mariadb\data" --port=%%MARIADB_PORT%% --console');
    BatFile.Add('timeout /t 5 /nobreak >nul');
    BatFile.Add('');
    
    // Запуск Grafana
    BatFile.Add('echo Starting Grafana...');
    BatFile.Add('start "Grafana" /MIN "%%COCLA_HOME%%grafana\bin\grafana-server.exe" --homepath="%%COCLA_HOME%%grafana" --config="%%COCLA_HOME%%grafana\conf\custom.ini"');
    BatFile.Add('timeout /t 5 /nobreak >nul');
    BatFile.Add('');
    
    // Запуск COCLA
    BatFile.Add('echo Starting COCLA...');
    BatFile.Add('"%%COCLA_HOME%%java\jdk-*\bin\java.exe" -jar "%%COCLA_HOME%%cocla.jar"');
    BatFile.Add('');
    
    // Открыть браузер
    BatFile.Add('start http://localhost:%%GRAFANA_PORT%%');
    BatFile.Add('');
    BatFile.Add('echo.');
    BatFile.Add('echo COCLA is running!');
    BatFile.Add('echo MariaDB: localhost:%%MARIADB_PORT%%');
    BatFile.Add('echo Grafana: http://localhost:%%GRAFANA_PORT%%');
    BatFile.Add('echo.');
    BatFile.Add('pause');
    
    BatFile.SaveToFile(BatPath);
    Log('start.bat created');
  finally
    BatFile.Free;
  end;
end;

// Создать stop.bat
procedure CreateStopBat;
var
  BatFile: TStringList;
  BatPath: String;
begin
  BatPath := ExpandConstant('{app}\stop.bat');
  BatFile := TStringList.Create;
  try
    BatFile.Add('@echo off');
    BatFile.Add('echo Stopping COCLA...');
    BatFile.Add('taskkill /IM java.exe /F 2>nul');
    BatFile.Add('echo Stopping Grafana...');
    BatFile.Add('taskkill /IM grafana-server.exe /F 2>nul');
    BatFile.Add('echo Stopping MariaDB...');
    BatFile.Add('taskkill /IM mysqld.exe /F 2>nul');
    BatFile.Add('echo All services stopped.');
    BatFile.Add('pause');
    
    BatFile.SaveToFile(BatPath);
    Log('stop.bat created');
  finally
    BatFile.Free;
  end;
end;

// Создать config.properties для COCLA
procedure CreateCOCLAConfig;
var
  ConfigFile: TStringList;
  ConfigPath: String;
begin
  ConfigPath := ExpandConstant('{app}\config\cocla\config.properties');
  ConfigFile := TStringList.Create;
  try
    ConfigFile.Add('db.host=localhost');
    ConfigFile.Add('db.port=' + MariaDBPort);
    ConfigFile.Add('db.name=cocla');
    ConfigFile.Add('db.user=cocla');
    ConfigFile.Add('db.password=' + CoclaDBPassword);
    ConfigFile.Add('db.useSSL=false');
    ConfigFile.Add('db.allowPublicKeyRetrieval=true');
    ConfigFile.Add('log.directory=' + GameLogDir);
    ConfigFile.Add('watch.interval=5000');
    
    ConfigFile.SaveToFile(ConfigPath);
    Log('config.properties created');
  finally
    ConfigFile.Free;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    // Установка Java
    if InstallJava then
      ExtractOpenJDK;
    
    // Установка MariaDB
    if InstallMariaDB then
    begin
      InstallMariaDBSilent;
      ExecuteSchemaSQL;
    end;
    
    // Установка Grafana
    if InstallGrafana then
    begin
      InstallGrafanaSilent;
      ConfigureGrafanaProvisioning;
    end;
    
    // Создать конфиги и скрипты
    CreateCOCLAConfig;
    CreateStartBat;
    CreateStopBat;
    
    Log('Installation completed successfully');
  end;
end;

// Добавление JAVA_HOME в PATH
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  // cleanup при деинсталляции
end;
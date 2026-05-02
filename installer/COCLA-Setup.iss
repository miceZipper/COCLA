; COCLA Installer Script - Simplified version
; No custom settings page, just component selection

#define AppName "COCLA"
#define AppVersion "1.0"
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
Name: "custom"; Description: "Custom installation - Choose which components to install"; Flags: iscustom

[Components]
Name: "java"; Description: "OpenJDK 26 (Portable)"; Types: typical custom
Name: "mariadb"; Description: "MariaDB 12.2.2"; Types: typical custom
Name: "grafana"; Description: "Grafana 13.0.1"; Types: typical custom
Name: "cocla"; Description: "COCLA Core Application"; Types: typical custom; Flags: fixed

[Files]
; COCLA
Source: "..\target\cocla-{#AppVersion}.jar"; DestDir: "{app}"; DestName: "cocla.jar"; Flags: ignoreversion; Components: cocla
Source: "..\config\mariadb\schema.sql"; DestDir: "{app}\config\cocla"; Flags: ignoreversion; Components: mariadb cocla
Source: "..\config\cocla\config.properties.example"; DestDir: "{app}\config\cocla"; DestName: "config.properties"; Flags: ignoreversion; Components: cocla

; Grafana provisioning
Source: "..\config\grafana\provisioning\datasources\mysql-cocla.yml"; DestDir: "{app}\config\grafana\provisioning\datasources"; Flags: ignoreversion; Components: grafana
Source: "..\config\grafana\provisioning\dashboards\cocla.yml"; DestDir: "{app}\config\grafana\provisioning\dashboards"; Flags: ignoreversion; Components: grafana
Source: "..\config\grafana\provisioning\dashboards\cocla\cocla-dashboard.json"; DestDir: "{app}\config\grafana\provisioning\dashboards\cocla"; Flags: ignoreversion; Components: grafana

; Батники
Source: "start.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "stop.bat"; DestDir: "{app}"; Flags: ignoreversion

; Redistributables
Source: "..\redist\win64\openjdk-26_windows-x64_bin.zip"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: java
Source: "..\redist\win64\mariadb-12.2.2-winx64.msi"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: mariadb
Source: "..\redist\win64\grafana_13.0.1_24542347077_windows_amd64.msi"; DestDir: "{tmp}"; Flags: deleteafterinstall; Components: grafana

[Icons]
Name: "{group}\Start COCLA"; Filename: "{app}\start.bat"; WorkingDir: "{app}"
Name: "{group}\Stop COCLA"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"
Name: "{group}\COCLA Dashboard"; Filename: "http://localhost:{#DefaultGrafanaPort}"
Name: "{group}\Uninstall COCLA"; Filename: "{uninstallexe}"

[Code]
var
  InstallJava, InstallMariaDB, InstallGrafana: Boolean;

// Unzip через Shell.Application
function UnzipShell(ZipFile, DestDir: String): Boolean;
var
  Shell, SrcFldr, DstFldr: Variant;
begin
  Result := False;
  try
    Shell := CreateOleObject('Shell.Application');
    SrcFldr := Shell.NameSpace(ZipFile);
    DstFldr := Shell.NameSpace(DestDir);
    if VarIsClear(SrcFldr) or VarIsClear(DstFldr) then Exit;
    DstFldr.CopyHere(SrcFldr.Items, 4 or 16);
    Sleep(5000);
    Result := True;
  except
    Result := False;
  end;
end;

// Извлечение OpenJDK
function ExtractOpenJDK: Boolean;
var
  ZipFile, ExtractPath: String;
begin
  Result := False;
  ZipFile := ExpandConstant('{tmp}\openjdk-26_windows-x64_bin.zip');
  ExtractPath := ExpandConstant('{app}\java');
  
  if not DirExists(ExtractPath) then
    CreateDir(ExtractPath);
    
  Result := UnzipShell(ZipFile, ExtractPath);
  if Result then
    Log('OpenJDK extracted successfully')
  else
    Log('ERROR: Failed to extract OpenJDK');
end;

// Установка MariaDB (тихо, без службы)
function InstallMariaDBSilent: Boolean;
var
  ResultCode: Integer;
  MSIPath, InstallDir, DataDir: String;
begin
  Result := False;
  MSIPath := ExpandConstant('{tmp}\mariadb-12.2.2-winx64.msi');
  InstallDir := ExpandConstant('{app}\mariadb');
  DataDir := ExpandConstant('{app}\mariadb\data');
  
  if not FileExists(MSIPath) then
  begin
    Log('ERROR: MariaDB MSI not found');
    Exit;
  end;
  
  if Exec('msiexec', '/i "' + MSIPath + '" /quiet /norestart ' +
     'INSTALLDIR="' + InstallDir + '" ' +
     'DATADIR="' + DataDir + '" ' +
     'SERVICENAME="" ' +
     'InstallService=0 ' +
     'PORT={#DefaultMariaDBPort} ' +
     'PASSWORD="root" ' +
     'ADDLOCAL=MYSQLSERVER,DBInstance,Client,SharedClientServerComponents,Readme,Common,RuntimeDeps,VCCRT',
     '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    Log('MariaDB MSI result code: ' + IntToStr(ResultCode));
    Sleep(10000);
    Result := FileExists(InstallDir + '\bin\mysql.exe');
  end
  else
    Log('ERROR: Failed to execute MariaDB MSI');
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
  
  if not FileExists(MSIPath) then
  begin
    Log('ERROR: Grafana MSI not found');
    Exit;
  end;
  
  if Exec('msiexec', '/i "' + MSIPath + '" /quiet /norestart ' +
     'INSTALLDIR="' + InstallDir + '" ' +
     'ADDLOCAL=GrafanaOSS',
     '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    Log('Grafana MSI result code: ' + IntToStr(ResultCode));
    Sleep(5000);
    Result := FileExists(InstallDir + '\bin\grafana-server.exe');
  end
  else
    Log('ERROR: Failed to execute Grafana MSI');
end;

// Выполнение schema.sql
procedure ExecuteSchemaSQL;
var
  SQLPath, MysqlExe: String;
  ResultCode: Integer;
begin
  SQLPath := ExpandConstant('{app}\config\cocla\schema.sql');
  MysqlExe := ExpandConstant('{app}\mariadb\bin\mysql.exe');
  
  if not FileExists(SQLPath) or not FileExists(MysqlExe) then
  begin
    Log('Schema.sql or mysql.exe not found');
    Exit;
  end;
  
  // Сначала запускаем MariaDB чтобы выполнить schema
  Exec(ExpandConstant('{app}\mariadb\bin\mysqld'), 
       '--datadir="' + ExpandConstant('{app}\mariadb\data') + '" --port={#DefaultMariaDBPort} --console',
       '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(5000);
  
  Exec(MysqlExe, '-u root -proot -P {#DefaultMariaDBPort} < "' + SQLPath + '"',
       '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  
  // Останавливаем
  Exec('taskkill', '/IM mysqld.exe /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    InstallJava := WizardIsComponentSelected('java');
    InstallMariaDB := WizardIsComponentSelected('mariadb');
    InstallGrafana := WizardIsComponentSelected('grafana');
    
    if InstallJava then
      ExtractOpenJDK;
    
    if InstallMariaDB then
    begin
      InstallMariaDBSilent;
      ExecuteSchemaSQL;
    end;
    
    if InstallGrafana then
      InstallGrafanaSilent;
  end;
end;

// Деинсталляция
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
  AppDir: String;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    AppDir := ExpandConstant('{app}');
    
    // 1. Остановить все процессы
    Exec('taskkill', '/IM java.exe /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Exec('taskkill', '/IM grafana-server.exe /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Exec('taskkill', '/IM mysqld.exe /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Sleep(2000);
    
    // 2. Удалить MariaDB через MSI
    if DirExists(AppDir + '\mariadb') then
      Exec('msiexec', '/x {E2531A42-27B6-44FE-B46E-3464C72D6E6A} /qb',
           '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    
    // 3. Удалить Grafana через MSI (замени на реальный ProductCode)
    if DirExists(AppDir + '\grafana') then
      Exec('msiexec', '/x {95B77BE6-10C4-4B91-BBC4-88326D7D237F} /qb',
           '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    
    Sleep(3000);
    
    // 4. Удалить папку целиком
    DelTree(AppDir, True, True, True);
  end;
end;
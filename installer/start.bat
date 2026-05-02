@echo off
title COCLA v1.1

if not defined COCLA_HOME set COCLA_HOME=%~dp0

set MARIADB_PORT=%COCLA_MARIADB_PORT%
set GRAFANA_PORT=%COCLA_GRAFANA_PORT%
if "%MARIADB_PORT%"=="" set MARIADB_PORT=3306
if "%GRAFANA_PORT%"=="" set GRAFANA_PORT=3000

:check_mariadb_port
netstat -an | find ":%MARIADB_PORT%" >nul
if %errorlevel% EQU 0 (
    set /a MARIADB_PORT+=1
    goto check_mariadb_port
)

:check_grafana_port
netstat -an | find ":%GRAFANA_PORT%" >nul
if %errorlevel% EQU 0 (
    set /a GRAFANA_PORT+=1
    goto check_grafana_port
)

echo Starting MariaDB on port %MARIADB_PORT%...
start "MariaDB" /MIN "%COCLA_HOME%\mariadb\bin\mysqld" --datadir="%COCLA_HOME%\mariadb\data" --port=%MARIADB_PORT% --console
timeout /t 5 /nobreak >nul

echo Starting Grafana on port %GRAFANA_PORT%...
start "Grafana" /MIN "%COCLA_HOME%\grafana\bin\grafana-server.exe" --homepath="%COCLA_HOME%\grafana" --config="%COCLA_HOME%\grafana\conf\custom.ini"
timeout /t 5 /nobreak >nul

echo Starting COCLA...
"%COCLA_HOME%\java\bin\java.exe" -jar "%COCLA_HOME%\cocla.jar"

start http://localhost:%GRAFANA_PORT%

echo COCLA running: Grafana http://localhost:%GRAFANA_PORT%, MariaDB localhost:%MARIADB_PORT%
pause
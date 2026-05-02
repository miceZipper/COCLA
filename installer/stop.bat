@echo off
echo Stopping COCLA...
taskkill /IM java.exe /F 2>nul
taskkill /IM grafana-server.exe /F 2>nul
taskkill /IM mysqld.exe /F 2>nul
echo All services stopped.
pause
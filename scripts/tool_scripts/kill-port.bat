@echo off
if "%~1"=="" (
    echo Usage: %~n0 port
    echo Example: %~n0 8080
    exit /b
)

set "port=%~1"
set "pid="

for /f "tokens=5" %%i in ('netstat -ano ^| findstr :%port%') do (
    set "pid=%%i"
)

if not defined pid (
    echo Port %port% is not in use, skipping.
    exit /b
)

echo Detected PID %pid% using port %port%, killing...
taskkill /F /PID %pid%

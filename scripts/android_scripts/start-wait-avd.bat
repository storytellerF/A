@echo off
setlocal enabledelayedexpansion

:: Set the AVD name
set AVD_NAME=ATest

:: Function to check if the AVD is running
:CHECK_AVD
echo Checking if AVD "%AVD_NAME%" is running...
adb devices | findstr /I "emulator" >nul
if %errorlevel% == 0 (
    echo AVD "%AVD_NAME%" is running!
    exit /b 0
) else (
    echo AVD "%AVD_NAME%" is not running.
)

:: Start the AVD if it's not running
echo Starting AVD "%AVD_NAME%"...
start emulator -avd %AVD_NAME% -no-snapshot-load

:: Wait for AVD to fully boot
set /a counter=0
:WAIT_FOR_BOOT
if %counter% GEQ 60 (
    echo Timeout reached. AVD did not start.
    exit /b 1
)
adb shell getprop sys.boot_completed | findstr "1" >nul
if %errorlevel% == 0 (
    echo AVD "%AVD_NAME%" is fully booted!
    exit /b 0
) else (
    echo Waiting for AVD to finish booting...
    timeout /t 5 /nobreak >nul
    set /a counter+=1
    goto WAIT_FOR_BOOT
)

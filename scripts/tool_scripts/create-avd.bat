@echo off
setlocal enabledelayedexpansion

:: 设置模拟器名称和设备参数
set AVD_NAME=ATest
set SYSTEM_IMAGE=system-images;android-35;google_apis_playstore;x86_64
set DEVICE=pixel_4

:: Check if AVD already exists
echo Checking if AVD "%AVD_NAME%" exists...
avdmanager list avd | findstr /C:"Name: %AVD_NAME%" >nul
if %errorlevel% == 0 (
    echo AVD "%AVD_NAME%" already exists. Skipping creation.
    exit /b
)

echo AVD "%AVD_NAME%" does not exist. Checking system image...

:: Check if the system image is installed
sdkmanager --list | findstr /C:"%SYSTEM_IMAGE%" >nul
if %errorlevel% neq 0 (
    echo System image "%SYSTEM_IMAGE%" is not installed. Downloading...
    sdkmanager "%SYSTEM_IMAGE%"
    if %errorlevel% neq 0 (
        echo Failed to download system image. Please check your SDK Manager.
        exit /b
    )
    echo System image downloaded successfully.
) else (
    echo System image is already installed. Skipping download.
)

:: Create the new AVD
echo Creating AVD "%AVD_NAME%"...
avdmanager create avd -n "%AVD_NAME%" -k "%SYSTEM_IMAGE%" -d "%DEVICE%"
if %errorlevel% neq 0 (
    echo Failed to create AVD. Please check your SDK configuration.
    exit /b
)
echo AVD "%AVD_NAME%" created successfully!

:: Ask the user whether to start the emulator
set /p START_AVD=Do you want to start the emulator now? (Y/N):
if /I "%START_AVD%"=="Y" (
    echo Starting AVD "%AVD_NAME%"...
    start emulator -avd "%AVD_NAME%"
) else (
    echo You can start it later using: emulator -avd %AVD_NAME%
)

exit /b


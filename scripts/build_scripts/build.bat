@echo off
@setlocal enabledelayedexpansion

set AVD_NAME=ATest
cmd /c .\gradlew.bat build || exit /b
start cmd /c .\gradlew.bat app:devCli:run

echo "create avd"
cmd /c .\scripts\android_scripts\create-avd.bat || exit /b
echo "start avd"
cmd /c .\scripts\android_scripts\start-wait-avd.bat || exit /b

echo Waiting for port 8888 to become available...
:wait_for_port
    netstat -ano | findstr :8888 >nul
    if %errorlevel% neq 0 (
        echo Port 8888 is not available yet. Waiting...
        timeout /t 5 >nul
        goto wait_for_port
    )

echo Port 8888 is now available. Continuing execution...

cmd /c .\gradlew.bat app:composeApp:connectedAndroidTest || exit /b
cmd /c .\gradlew.bat app:composeApp:desktopTest || exit /b
@REM ./gradlew :composeApp:wasmJsTest
@REM ./gradlew :composeApp:iosSimulatorArm64Test
for /f "delims=" %%i in ('netstat -ano ^| findstr :8888 ^| sort ^| findstr /r "[0-9][0-9]*$"') do (
    for /f "tokens=5" %%j in ("%%i") do (
        if not defined PID_%%j (
            set PID_%%j=1
            taskkill /PID %%j /F
        )
    )
)

adb emu kill

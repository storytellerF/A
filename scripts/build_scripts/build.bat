cmd /c .\gradlew.bat build || exit /b
start cmd /c .\gradlew.bat test-server:run

echo Waiting for port 8888 to become available...
:wait_for_port
    netstat -ano | findstr :8888 >nul
    if %errorlevel% neq 0 (
        echo Port 8888 is not available yet. Waiting...
        timeout /t 5 >nul
        goto wait_for_port
    )

echo Port 8888 is now available. Continuing execution...

cmd /c .\gradlew.bat :composeApp:connectedAndroidTest || exit /b
cmd /c .\gradlew.bat :composeApp:desktopTest || exit /b
@REM ./gradlew :composeApp:wasmJsTest
@REM ./gradlew :composeApp:iosSimulatorArm64Test
cmd /c "for /f \"tokens=5\" %i in ('netstat -ano ^| findstr :8888') do taskkill /PID %i /F"

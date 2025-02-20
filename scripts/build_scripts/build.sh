./gradlew build
./gradlew test-server:run
./gradlew :composeApp:connectedAndroidTest
./gradlew :composeApp:desktopTest
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
#cmd /c "for /f \"tokens=5\" %i in ('netstat -ano ^| findstr :8888') do taskkill /PID %i /F\n"
kill -9 $(lsof -t -i :8888>)
#kill -9 $(netstat -tuln | grep :8888 | awk '{print $7}' | cut -d'/' -f1)

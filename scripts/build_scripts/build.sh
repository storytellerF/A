./gradlew build
./gradlew test-server:run
./gradlew :composeApp:connectedAndroidTest
./gradlew :composeApp:desktopTest
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
kill -9 $(lsof -t -i :8888>)
#kill -9 $(netstat -tuln | grep :8888 | awk '{print $7}' | cut -d'/' -f1)

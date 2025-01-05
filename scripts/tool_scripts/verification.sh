#!sh

./gradlew build
./gradlew :composeApp:connectedAndroidTest
./gradlew :composeApp:desktopTest
./gradlew :composeApp:wasmJsTest
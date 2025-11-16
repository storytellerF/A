#!/bin/sh
set -e

if [ -f /usr/local/bin/install-sdk.sh ]; then
    echo "Found /usr/local/bin/install-sdk.sh, executing..."
    /usr/local/bin/install-sdk.sh
    echo "SDK setup finished."
else
    echo "/usr/local/bin/install-sdk.sh not found, skipping."
fi

shutdownEmu() {
    echo "Shutting down emulator gracefully..."
    adb emu kill
    echo "Emulator shut down."
}

shutdownServer() {
    echo "Shutting down server gracefully..."
    pid=$(lsof -t -i :8888) && kill -9 "$pid"
    echo "Server shut down."
}

./gradlew cloud:server:installDist --no-daemon
./gradlew build --no-daemon
./scripts/android_scripts/create-avd.sh ATest "system-images;android-30;default;x86_64" "pixel4"
./scripts/android_scripts/start-avd.sh ATest
trap shutdownEmu EXIT

./scripts/test_scripts/start-test-server.sh
trap shutdownServer EXIT

./gradlew app:composeApp:connectedAndroidTest --no-daemon
./gradlew app:composeApp:desktopTest --no-daemon
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test

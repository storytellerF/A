#!/bin/sh
set -e

if [ -f /usr/local/bin/install-sdk.sh ]; then
    echo "Found /usr/local/bin/install-sdk.sh, executing..."
    /usr/local/bin/install-sdk.sh
    echo "SDK setup finished."
else
    echo "/usr/local/bin/install-sdk.sh not found, skipping."
fi

./gradlew build
./gradlew app:devCli:run &
./scripts/android_scripts/create-avd.sh ATest "system-images;android-30;default;x86_64" "pixel4"
./scripts/android_scripts/start-wait-avd.sh ATest
echo "Waiting for port 8888 to become available..."
counter=0
max_retries=60  # 最多尝试 60 次（5 分钟）

while true; do
    if ! netstat -an | grep -q ":8888"; then
        echo "Port 8888 is now available. Continuing execution..."
        break
    fi

    echo "Port 8888 is not available yet. Waiting..."
    sleep 5

    counter=$((counter + 1))
    if [ "$counter" -ge "$max_retries" ]; then
        echo "Timeout reached. Port 8888 is still unavailable."
        exit 1
    fi
done
./gradlew app:composeApp:connectedAndroidTest
./gradlew app:composeApp:desktopTest
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
pid=$(lsof -t -i :8888) && kill -9 "$pid"
adb emu kill

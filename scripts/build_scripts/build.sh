#!/bin/sh

set -e
./gradlew build
./scripts/tool_scripts/create-avd.bat
./scripts/tool_scripts/start-wait-avd.bat
./gradlew test-server:run &
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

    ((counter++))
    if [ "$counter" -ge "$max_retries" ]; then
        echo "Timeout reached. Port 8888 is still unavailable."
        exit 1
    fi
done
./gradlew :composeApp:connectedAndroidTest
./gradlew :composeApp:desktopTest
#./gradlew :composeApp:wasmJsTest
#./gradlew :composeApp:iosSimulatorArm64Test
kill -9 $(lsof -t -i :8888>)
#kill -9 $(netstat -tuln | grep :8888 | awk '{print $7}' | cut -d'/' -f1)
adb emu kill

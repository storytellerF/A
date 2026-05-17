#!/usr/bin/env sh
set -e
./gradlew cloud:server:installDist
./gradlew dev:server:installDist
./dev/server/build/install/server/bin/server &
echo "Waiting for port 8888 to become available..."
counter=0
max_retries=60  # 最多尝试 60 次（5 分钟）

while [ "$counter" -lt "$max_retries" ]; do
    if ! netstat -an | grep -q ":8888"; then
        echo "Port 8888 is now available. Continuing execution..."
        exit 0
    fi

    echo "Port 8888 is not available yet. Waiting..."
    sleep 5

    counter=$((counter + 1))
done

echo "Timeout reached. Port 8888 is still unavailable."
exit 1
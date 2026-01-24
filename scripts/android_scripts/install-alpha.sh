#!/bin/sh

./scripts/tool_scripts/modify-flavor.sh alpha prod

./gradlew app:android:installRelease --no-daemon
./gradlew panel:android:installRelease --no-daemon
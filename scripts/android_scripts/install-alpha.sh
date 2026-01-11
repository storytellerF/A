#!/bin/sh

./scripts/tool_scripts/modify-flavor.sh alpha prod

./gradlew app:composeApp:installRelease --no-daemon
./gradlew panel:composeApp:installRelease --no-daemon
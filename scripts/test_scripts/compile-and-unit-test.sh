#!/bin/sh

set -e
# ./gradlew clean --no-daemon
./scripts/tool_scripts/exec-until-success.sh ./gradlew detekt --no-daemon
./gradlew cloud:server:installDist --no-daemon
ENABLE_TEST_CONTAINER=true ./gradlew build --no-daemon

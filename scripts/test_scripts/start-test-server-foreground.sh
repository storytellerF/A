#!/bin/sh
set -e
./gradlew dev:server:installDist
./dev/server/build/install/server/bin/server
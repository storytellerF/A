#!/bin/sh
set -e
mkdir -p deploy/build
./gradlew bot:builtin-bot:distZip bot:builtin-bot:distTar --no-daemon
cp bot/builtin-bot/build/distributions/* deploy/build

#!/bin/sh
set -e
./gradlew bot:builtin-bot:distZip bot:builtin-bot:distTar --no-daemon

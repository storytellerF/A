#!/bin/sh
set -e
./gradlew bot:builtin-bot:distZip cloud:worker:distTar --no-daemon

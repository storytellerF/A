#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew bot:builtin-bot:distZip bot:builtin-bot:distTar
cp bot/builtin-bot/build/distributions/* deploy/build

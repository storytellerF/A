#!/bin/sh
set -e

FLAVOR=$1
BUILD_TYPE=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "FLAVOR and BUILD_TYPE must be set"
  exit 1
fi

mkdir -p deploy/build
./gradlew bot:builtin-bot:distZip bot:builtin-bot:distTar --no-daemon -Dflavor="$FLAVOR" -DbuildType="$BUILD_TYPE"
cp bot/builtin-bot/build/distributions/* deploy/build

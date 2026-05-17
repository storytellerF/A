#!/bin/sh
set -e

FLAVOR=$1
BUILD_TYPE=$2

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

if [ -z "$BUILD_TYPE" ]; then
  echo "BUILD_TYPE must be set"
  exit 1
fi

mkdir -p deploy/build
./gradlew bot:builtin-bot:distZip bot:builtin-bot:distTar -Dflavor="$FLAVOR" -DbuildType="$BUILD_TYPE"
cp bot/builtin-bot/build/distributions/* deploy/build

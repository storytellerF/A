#!/bin/sh
set -e

FLAVOR=$1
BUILD_TYPE=$2
if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "FLAVOR and BUILD_TYPE must be set"
  exit 1
fi

mkdir -p deploy/build
./gradlew cloud:server:distTar cloud:server:distZip --no-daemon -Pserver.flavor=$FLAVOR -Pserver.buildType=$BUILD_TYPE
cp cloud/server/build/distributions/* deploy/build
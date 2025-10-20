#!/bin/sh
FLAVOR=$1
BUILD_TYPE=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "FLAVOR and BUILD_TYPE must be set"
  exit 1
fi

if [ "$(uname)" = "Darwin" ]; then
    sed -i '' "s/server.flavor=.*/server.flavor=${FLAVOR}/" gradle.properties && \
      sed -i '' "s/server.buildType=dev/server.buildType=${BUILD_TYPE}/" gradle.properties
else
    sed -i "s/server.flavor=.*/server.flavor=${FLAVOR}/" gradle.properties && \
      sed -i "s/server.buildType=dev/server.buildType=${BUILD_TYPE}/" gradle.properties
fi

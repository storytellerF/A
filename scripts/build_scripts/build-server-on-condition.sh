#!/bin/sh
set -e
FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "FLAVOR and BUILD_TYPE must be set"
  exit 1
fi
if [ -z "$HOST_TYPE" ] || [ -z "$BUILD_ON" ]; then
  echo "Error: HOST_TYPE and BUILD_ON must be set."
  exit 1
fi

if [ "$BUILD_ON" = "$HOST_TYPE" ]; then
  echo "Proceeding with build...[${HOST_TYPE}]"
else
  echo "BUILD_ON and HOST_TYPE mismatch, skip build."
  exit 0
fi

mkdir -p deploy/build
./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$BUILD_TYPE"
./scripts/build_scripts/build-server.sh && ./scripts/build_scripts/build-cli.sh

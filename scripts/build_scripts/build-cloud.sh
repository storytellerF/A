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
./scripts/build_scripts/build-server.sh "$FLAVOR" "$BUILD_TYPE"
./scripts/build_scripts/build-cli.sh "$FLAVOR" "$BUILD_TYPE"

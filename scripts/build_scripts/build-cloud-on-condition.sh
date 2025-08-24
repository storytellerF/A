#!/bin/sh
set -e
FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

./scripts/build_scripts/build-on-condition.sh "$FLAVOR" "$BUILD_TYPE" "$BUILD_ON" "./scripts/build_scripts/build-cloud.sh"

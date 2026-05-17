#!/bin/sh
set -e
FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
  exit 1
fi

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set"
  exit 1
fi

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

if [ -z "$BUILD_TYPE" ]; then
  echo "BUILD_TYPE must be set"
  exit 1
fi

./scripts/build_scripts/build-on-condition.sh "$BUILD_ON" "./scripts/build_scripts/build-cloud.sh $FLAVOR $BUILD_TYPE"

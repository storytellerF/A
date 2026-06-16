#!/bin/sh
set -e
BUILD_TYPE=$1

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set"
  exit 1
fi

./scripts/build_scripts/build-on-condition.sh "$BUILD_ON" "./scripts/build_scripts/build-cloud.sh

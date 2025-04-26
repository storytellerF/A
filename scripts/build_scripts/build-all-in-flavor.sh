#!/bin/sh

set -e
FLAVOR=$1
BUILD_TYPE=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "FLAVOR and BUILD_TYPE must be set"
  exit 1
fi

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$BUILD_TYPE"

./scripts/build_scripts/build-server-on-condition.sh
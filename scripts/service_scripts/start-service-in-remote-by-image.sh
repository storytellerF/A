#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1
BUILD_ON=$2
REMOTE_URI=$3

if [ -z "$FLAVOR" ] || [ -z "$BUILD_ON" ]; then
  echo "FLAVOR and BUILD_ON must be set"
  exit 1
fi

if [ -z "$REMOTE_URI" ]; then
  echo "REMOTE_URI must be set"
  exit 1
fi

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$BUILD_TYPE"

./scripts/build_scripts/build-cloud.sh
./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod local
./scripts/push_scripts/push-image-to-remote.sh "$REMOTE_URI"
ssh "$REMOTE_URI" "cd Projects/A && git stash && git pull && ./scripts/service-scripts/start-service-at-remote-by-image.sh $FLAVOR"
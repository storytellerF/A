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

if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_COMMAND" ]; then
  echo "FLAVOR and BUILD_ON must be set"
    exit 1
fi

./scripts/build_scripts/build-cloud.sh "$FLAVOR" prod
./scripts/push_scripts/push-jar-to-remote.sh "$REMOTE_URI"
ssh "$REMOTE_URI" "./scripts/service-scripts/start-service-at-remote-by-jar.sh $FLAVOR"
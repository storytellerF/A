#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1
BUILD_ON=$2
REMOTE_URI=$3

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
  exit 1
fi

if [ -z "$REMOTE_URI" ]; then
  echo "REMOTE_URI must be set"
  exit 1
fi

if [ -z "$REMOTE_COMMAND" ]; then
  echo "REMOTE_COMMAND must be set"
  exit 1
fi

./scripts/build_scripts/build-cloud.sh "$FLAVOR" prod
./scripts/push_scripts/push-jar-to-remote.sh "$REMOTE_URI"
ssh "$REMOTE_URI" "./scripts/service-scripts/start-service-at-remote-by-jar.sh $FLAVOR"
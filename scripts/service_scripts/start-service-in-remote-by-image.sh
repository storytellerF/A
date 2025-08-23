#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1
BUILD_ON=$2
REMOTE_URI=$3
REMOTE_CERT_FILE=$4
REMOTE_COMMAND=$5

if [ -z "$FLAVOR" ] || [ -z "$BUILD_ON" ]; then
  echo "FLAVOR and BUILD_ON must be set"
  exit 1
fi

if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
  echo "FLAVOR and BUILD_ON must be set"
  exit 1
fi

./scripts/build_scripts/build-cloud.sh "$FLAVOR" prod local
./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod local
./scripts/push_scripts/push-image-to-remote.sh "$REMOTE_URI"
./scripts/service_scripts/start-service-at-remote-by-image.sh
#!/bin/bash

. ./scripts/tool_scripts/terminal-log.sh

set -e

if [ -z "$HOST_TYPE" ]; then
  log "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1
BUILD_ON=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_ON" ]; then
  log "FLAVOR and BUILD_ON must be set"
  exit 1
fi

log "build cloud image"
./scripts/build_scripts/build-cloud.sh "$FLAVOR" prod
log "build server image"
./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod local
log "push image to remote"
./scripts/push_scripts/push-image-to-remote.sh "$FLAVOR"
log "start service at remote"
ssh default "cd Projects/A && git stash && git pull && ./scripts/service_scripts/start-service-at-remote-by-image.sh $FLAVOR"

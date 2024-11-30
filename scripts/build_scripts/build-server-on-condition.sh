#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ] || [ -z "$BUILD_ON" ]; then
  echo "Error: HOST_TYPE and BUILD_ON must be set."
  exit 1
fi

if [ "$BUILD_ON" = "$HOST_TYPE" ]; then
  echo "Proceeding with build...[${HOST_TYPE}]"
else
  echo "Never build."
  exit 0
fi

mkdir -p deploy/build
./scripts/build_scripts/build-server.sh && ./scripts/build_scripts/build-cli.sh

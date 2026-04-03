#!/bin/sh

set -e

FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ] || [ -z "$BUILD_ON" ]; then
  echo "Usage: $0 <FLAVOR> <BUILD_TYPE> <BUILD_ON>"
  echo "Example: $0 dev prod local"
  exit 1
fi

if [ "$BUILD_ON" = "host" ]; then
  echo "BUILD_ON=host, preparing host artifacts first"
  mkdir -p deploy/build
  ./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" "$BUILD_TYPE"
  ./scripts/build_scripts/build-cloud.sh
  ./scripts/build_scripts/build-worker.sh
fi

echo "Building server image: a-server:latest"
docker build --platform linux/amd64 \
  --build-arg BUILD_TYPE="$BUILD_TYPE" \
  --build-arg FLAVOR="$FLAVOR" \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-server:latest \
  .

echo "Building worker image: a-worker:latest"
docker build --platform linux/amd64 \
  -f worker.Dockerfile \
  --build-arg BUILD_TYPE="$BUILD_TYPE" \
  --build-arg FLAVOR="$FLAVOR" \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-worker:latest \
  .

echo "Done. Built images: a-server:latest and a-worker:latest"

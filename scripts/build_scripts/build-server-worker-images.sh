#!/bin/sh

set -e

FLAVOR=$1
BUILD_TYPE=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_TYPE" ]; then
  echo "Usage: $0 <FLAVOR> <BUILD_TYPE>"
  echo "Example: $0 dev prod"
  exit 1
fi

mkdir -p deploy/build
./scripts/build_scripts/build-cloud.sh "$FLAVOR" prod
./scripts/build_scripts/build-worker.sh "$FLAVOR" prod

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

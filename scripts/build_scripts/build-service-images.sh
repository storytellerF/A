#!/bin/sh

set -e

FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  echo "Usage: $0 <FLAVOR> <BUILD_TYPE>"
  echo "Example: $0 dev prod"
  exit 1
fi

if [ -z "$BUILD_TYPE" ]; then
  echo "BUILD_TYPE must be set"
  echo "Usage: $0 <FLAVOR> <BUILD_TYPE>"
  echo "Example: $0 dev prod"
  exit 1
fi

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
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

echo "Building cli image: a-cli:latest"
docker build --platform linux/amd64 \
  -f cli.Dockerfile \
  --build-arg BUILD_TYPE="$BUILD_TYPE" \
  --build-arg FLAVOR="$FLAVOR" \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-cli:latest \
  .

echo "Building ws image: a-ws:latest"
docker build --platform linux/amd64 \
  -f ws.Dockerfile \
  --build-arg BUILD_TYPE="$BUILD_TYPE" \
  --build-arg FLAVOR="$FLAVOR" \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-ws:latest \
  .

echo "Done. Built images: a-server:latest, a-worker:latest, a-cli:latest and a-ws:latest"

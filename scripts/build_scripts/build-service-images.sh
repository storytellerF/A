#!/bin/sh

set -e

BUILD_ON=$1

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
  exit 1
fi

mkdir -p deploy/build
./scripts/build_scripts/build-cloud.sh

echo "Building server image: a-server:latest"
docker build --platform linux/amd64 \
  --target server \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-server:latest \
  .

echo "Building worker image: a-worker:latest"
docker build --platform linux/amd64 \
  --target worker \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-worker:latest \
  .

echo "Building cli image: a-cli:latest"
docker build --platform linux/amd64 \
  --target cli \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-cli:latest \
  .

echo "Building ws image: a-ws:latest"
docker build --platform linux/amd64 \
  --target ws \
  --build-arg BUILD_ON="$BUILD_ON" \
  -t a-ws:latest \
  .

echo "Done. Built images: a-server:latest, a-worker:latest, a-cli:latest and a-ws:latest"

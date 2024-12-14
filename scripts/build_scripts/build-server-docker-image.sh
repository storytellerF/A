#!/bin/sh
set -e
DOCKER_IMAGE_NAME=$1
if [ -z "$DOCKER_IMAGE_NAME" ]; then
  echo "Error: DOCKER_IMAGE_NAME must be set."
  exit 1
fi

HOST_TYPE=local \
  BUILD_ON=local \
  ./scripts/build_scripts/build-all-in-flavor.sh alpha true
docker build -t "$DOCKER_IMAGE_NAME" .

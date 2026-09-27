#!/bin/bash
set -euo pipefail

FLAVOR=${1:-}

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

ENV_FILE="./deploy/$FLAVOR.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "$ENV_FILE does not exist"
  exit 1
fi

echo "build in Docker"
export BUILD_ON=docker
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'

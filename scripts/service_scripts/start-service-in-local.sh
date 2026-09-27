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

echo "build on local"
# 在本地构建，本地启动
"${BUILD_CLOUD_SCRIPT:-./scripts/build_scripts/build-cloud.sh}"

export BUILD_ON=local
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'

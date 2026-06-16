#!/bin/bash
set -e
FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

echo "build on local"
# 在本地构建，本地启动
./scripts/build_scripts/build-cloud.sh
export BUILD_ON=local
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'

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

BUILD_TYPE=$(grep '^BUILD_TYPE=' "$ENV_FILE" | cut -d '=' -f2-)
if [ -z "$BUILD_TYPE" ]; then
  echo "BUILD_TYPE must be set in $ENV_FILE"
  exit 1
fi

IFS=',' read -ra COMPOSE_FILE_LIST <<< "$(grep '^COMPOSE_FILE_LIST=' "$ENV_FILE" | cut -d '=' -f2-)"
ENABLE_APP=false
for profile in "${COMPOSE_FILE_LIST[@]}"; do
  if [ "$(echo "$profile" | xargs)" = "app" ]; then
    ENABLE_APP=true
    break
  fi
done

echo "build on local"
# 在本地构建，本地启动
"${BUILD_CLOUD_SCRIPT:-./scripts/build_scripts/build-cloud.sh}"

if [ "$ENABLE_APP" = true ]; then
  "${GRADLEW:-./gradlew}" \
    :app:webApp:wasmJsBrowserDistribution \
    :panel:webApp:wasmJsBrowserDistribution \
    -Ptarget.wasm=true \
    -Pserver.flavor="$FLAVOR" \
    -Pserver.buildType="$BUILD_TYPE"
fi

export BUILD_ON=local
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'

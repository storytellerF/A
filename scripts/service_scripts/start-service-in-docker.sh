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

echo "build and start in Docker"
export BUILD_ON=docker

IFS=',' read -ra PROFILES <<< "$(grep '^COMPOSE_FILE_LIST=' "$ENV_FILE" | cut -d '=' -f2-)"

has_profile() {
  local target=$1
  local profile
  for profile in "${PROFILES[@]}"; do
    if [[ "$(echo "$profile" | xargs)" == "$target" ]]; then
      return 0
    fi
  done
  return 1
}

# Build one image at a time. Gradle and Wasm builders are both memory-heavy,
# and Compose otherwise builds independent services concurrently.
BUILD_SERVICES=()
for service in filesystem lucene cli worker server ws minio; do
  if has_profile "$service" || { [[ "$service" == "ws" ]] && has_profile server; }; then
    BUILD_SERVICES+=("$service")
  fi
done
if has_profile app; then
  BUILD_SERVICES+=(app-wasm panel-wasm)
fi

for service in "${BUILD_SERVICES[@]}"; do
  echo "build $service"
  ./scripts/service_scripts/compose-service.sh "$FLAVOR" false "build $service"
done

./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --no-build'

#!/bin/bash
set -euo pipefail

BUILD_ON=${1:-}
[[ -n "$BUILD_ON" ]] || { echo "BUILD_ON must be set" >&2; exit 1; }
mkdir -p deploy/build
if [[ "$BUILD_ON" != docker ]]; then
  ./scripts/build_scripts/build-cloud.sh
fi

cleanup() {
  local pid
  for pid in $(jobs -pr); do kill "$pid" 2>/dev/null || true; done
  wait || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

build_pids=()
for service in server worker cli ws; do
  echo "Building $service image: a-$service:latest"
  docker build --platform linux/amd64 --target "$service" \
    --build-arg "BUILD_ON=$BUILD_ON" -t "a-$service:latest" . &
  build_pids+=("$!")
done
result=0
for pid in "${build_pids[@]}"; do wait "$pid" || result=$?; done
[[ $result == 0 ]] || exit "$result"
echo "Done. Built images: a-server:latest, a-worker:latest, a-cli:latest and a-ws:latest"

#!/bin/bash
set -e
./scripts/tool_scripts/patch-koyeb-env.sh ./Dockerfile
args=$(grep -v '^#' ./koyeb.env | grep -v '^$' | awk -F '=' '{print "--build-arg " $1 "=\"" $2 "\""}' ORS=' ')
eval docker build "$args" \
  -f ./Dockerfile.patched \
  -t local-koyeb:latest .
docker run \
  --privileged \
  -d \
  -p 8811:8811 \
  -p 5432:5432 \
  -p 8812:8812 \
  local-koyeb:latest

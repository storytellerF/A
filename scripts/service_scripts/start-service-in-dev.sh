#!/usr/bin/env bash
FLAVOR=dev
# 检测操作系统
if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  FLAVOR=dev.win
fi
./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" dev
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'
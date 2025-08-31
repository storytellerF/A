#!/bin/bash
set -e
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi
FLAVOR=dev
# 检测操作系统
if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  FLAVOR=dev.win
fi
./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" dev
./gradlew cloud:cli:installDist
cd cloud/cli
cli_path=build/install/cli/bin/cli
../../scripts/tool_scripts/flush-database.sh $cli_path "../../$base"

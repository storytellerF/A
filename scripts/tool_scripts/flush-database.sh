#!/bin/sh
set -e
sh gradlew cli:installDist
cd cli
cli_path=build/install/cli/bin/cli
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi
../scripts/tool_scripts/flush-database-singleton.sh $cli_path "../$base"

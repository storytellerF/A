#!/bin/sh
set -e
base=$1
if [ -z "$base" ]; then
  echo "Error: base parameter is not specified."
  exit 1
fi

./gradlew cli:installDist
cd cli
cli_path=build/install/cli/bin/cli
../scripts/tool_scripts/flush-database.sh $cli_path "../$base"

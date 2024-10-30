#!/bin/bash
set -e
bash scripts/tool_scripts/check-all-env.sh
sh gradlew cli:distZip cli:distTar --no-daemon
cp ./cli/build/distributions/cli.zip ./deploy/build

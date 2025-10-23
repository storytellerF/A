#!/bin/sh
set -e
mkdir -p deploy/build
./scripts/build_scripts/build-server.sh
./scripts/build_scripts/build-cli.sh

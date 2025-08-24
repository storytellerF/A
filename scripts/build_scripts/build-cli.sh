#!/bin/sh
set -e
mkdir -p deploy/build
./gradlew cloud:cli:distZip cloud:cli:distTar --no-daemon
cp cloud/cli/build/distributions/* deploy/build

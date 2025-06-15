#!/bin/sh
set -e
./gradlew cloud:cli:distZip cloud:cli:distTar --no-daemon
cp ./cloud/cli/build/distributions/cli.zip ./deploy/build

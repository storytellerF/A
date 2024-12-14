#!/bin/sh
set -e
./gradlew cli:distZip cli:distTar --no-daemon
cp ./cli/build/distributions/cli.zip ./deploy/build

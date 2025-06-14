#!/bin/sh
set -e
./gradlew server:cli:distZip cli:distTar --no-daemon
cp ./server/cli/build/distributions/cli.zip ./deploy/build

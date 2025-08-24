#!/bin/sh
set -e
mkdir -p deploy/build
./gradlew cloud:worker:distZip cloud:worker:distTar --no-daemon
cp cloud/worker/libs/* deploy/build

#!/bin/sh
set -e
mkdir -p deploy/build
./gradlew cloud:server:distTar cloud:server:distZip --no-daemon
cp cloud/server/build/distributions/* deploy/build
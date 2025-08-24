#!/bin/sh
set -e
mkdir -p deploy/build
./gradlew cloud:server:buildFatJar --no-daemon
cp cloud/server/build/libs/* deploy/build
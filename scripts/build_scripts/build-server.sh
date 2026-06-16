#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew cloud:server:distTar cloud:server:distZip
cp cloud/server/build/distributions/* deploy/build
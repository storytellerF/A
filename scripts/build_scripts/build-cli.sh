#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew cloud:cli:distZip cloud:cli:distTar
cp cloud/cli/build/distributions/* deploy/build

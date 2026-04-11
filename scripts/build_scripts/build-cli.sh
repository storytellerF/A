#!/bin/sh
set -e

FLAVOR=$1
BUILD_TYPE=$2

mkdir -p deploy/build
./gradlew cloud:cli:distZip cloud:cli:distTar --no-daemon -Dflavor="$FLAVOR" -DbuildType="$BUILD_TYPE"
cp cloud/cli/build/distributions/* deploy/build

#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew cloud:worker:distZip cloud:worker:distTar
cp cloud/worker/build/distributions/* deploy/build

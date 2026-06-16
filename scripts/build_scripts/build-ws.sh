#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew cloud:ws:distTar cloud:ws:distZip
cp cloud/ws/build/distributions/* deploy/build

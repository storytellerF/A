#!/bin/sh
set -e
./gradlew cloud:server:buildFatJar --no-daemon
cp ./cloud/server/build/libs/*-all.jar ./deploy/build

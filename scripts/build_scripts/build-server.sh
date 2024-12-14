#!/bin/sh
set -e
./gradlew server:buildFatJar --no-daemon
cp ./server/build/libs/*-all.jar ./deploy/build

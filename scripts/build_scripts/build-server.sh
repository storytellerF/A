#!/bin/sh
set -e
./gradlew server:main:buildFatJar --no-daemon
cp ./server/main/build/libs/*-all.jar ./deploy/build

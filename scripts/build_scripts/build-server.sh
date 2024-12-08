#!/bin/bash
set -e
sh gradlew server:buildFatJar --no-daemon
cp ./server/build/libs/*-all.jar ./deploy/build

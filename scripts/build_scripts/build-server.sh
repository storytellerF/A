#!/bin/sh
set -e
./gradlew cloud:server:buildFatJar --no-daemon

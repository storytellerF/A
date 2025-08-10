#!/bin/sh
set -e
./gradlew cloud:cli:distZip cloud:cli:distTar --no-daemon

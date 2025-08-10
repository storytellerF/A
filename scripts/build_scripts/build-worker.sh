#!/bin/sh
set -e
./gradlew cloud:worker:distZip cloud:worker:distTar --no-daemon

#!/bin/sh
set -e

mkdir -p deploy/build
./gradlew \
    cloud:filesystem-service:distTar \
    cloud:lucene-service:distTar
cp cloud/filesystem-service/build/distributions/filesystem-service.tar deploy/build
cp cloud/lucene-service/build/distributions/lucene-service.tar deploy/build

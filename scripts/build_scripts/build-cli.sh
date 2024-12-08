#!/bin/bash
set -e
sh gradlew cli:distZip cli:distTar --no-daemon
cp ./cli/build/distributions/cli.zip ./deploy/build

#!/bin/bash
set -e
echo "build on local"
# 在本地构建，本地启动
./scripts/build_scripts/build-bot.sh
docker build --build-arg FLAVOR="$FLAVOR" --build-arg BUILD_ON=local --build-arg BUILD_TYPE=prod -t bot:latest -f bot.Dockerfile .

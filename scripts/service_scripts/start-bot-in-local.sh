#!/bin/bash
set -e
FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi
echo "build on local"
# 在本地构建，本地启动
./scripts/build_scripts/build-bot.sh
docker build --build-arg FLAVOR="$FLAVOR" --build-arg BUILD_ON=local --build-arg BUILD_TYPE=prod -t bot:latest -f bot.Dockerfile .
docker stop "$(docker ps -a -q --filter name="bot")"
docker run -itd --env-file "./$FLAVOR.env" -t bot bot:latest
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
ID=$(docker ps -a -q --filter name="bot")
if [ -n "$ID" ]; then
    docker stop $ID
else
    echo "没有找到 bot 容器"
fi

docker run -itd --env-file "./deploy/$FLAVOR.env" --name bot bot:latest

#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

REMOTE_URI=$2
REMOTE_CERT_FILE=$3
REMOTE_COMMAND=$4

while IFS= read -r line; do
  # Ignore empty lines and comments
  [[ -z "$line" || "$line" =~ ^# ]] && continue
  IFS='=' read -r key value <<<"$line"
  export "$key"="$value"
done <"$FLAVOR.env"

if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
  if [ "$HOST_TYPE" = "local" ]; then
    echo "build on local"
    # 在本地启动
    ./scripts/build_scripts/build-all-in-flavor.sh "$FLAVOR" true
    "./scripts/service_scripts/start-$FLAVOR-compose.sh" false 'up -d --build'
  else
    echo "build on remote"
    # 在远程主机上启动
    # load image
    docker load -i "/tmp/A/$FLAVOR.image.tar"
    cd deploy
    # 使用预构建镜像构建服务
    "../scripts/service_scripts/start-$FLAVOR-compose.sh" true 'up -d --build'
  fi
else
  # 在本地构建，然后发送docker image 到远程主机上启动
  echo "build for remote"
  # 定义要保存的文件名
  FILE="build/images/$FLAVOR.image.tar"

  # 检查文件是否存在
  if [ ! -f "$FILE" ]; then
    ./scripts/build_scripts/build-all-in-flavor.sh "$FLAVOR" true
    ./scripts/tool_scripts/exec-until-success.sh docker build --platform linux/amd64 --build-arg IS_PROD=$IS_PROD --build-arg FLAVOR=$FLAVOR --build-arg BUILD_ON=$BUILD_ON -t a-server:latest .
  else
    echo "$FILE already exists. Skipping docker image build."
  fi
  ./scripts/service_scripts/push-image-to-remote.sh "$REMOTE_URI" "$REMOTE_CERT_FILE" "$REMOTE_COMMAND $FLAVOR"
fi

#!/bin/bash
set -e

if [ -z "$HOST_TYPE" ]; then
  echo "HOST_TYPE must be set."
  exit 1
fi

FLAVOR=$1
BUILD_ON=$2

if [ -z "$FLAVOR" ] || [ -z "$BUILD_ON" ]; then
  echo "FLAVOR and BUILD_ON must be set"
  exit 1
fi

REMOTE_URI=$3
REMOTE_CERT_FILE=$4
REMOTE_COMMAND=$5

if [[ -n $REMOTE_URI && -n $REMOTE_CERT_FILE && -n $REMOTE_COMMAND ]]; then
  if [ "$HOST_TYPE" = "local" ]; then
      echo "HOST_TYPE must not be local"
      exit 1
  fi
   # 在本地构建，然后发送docker image 到远程主机上启动
    echo "build for remote"
    ./scripts/build_scripts/build-server-on-condition.sh "$FLAVOR" prod local
    ./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod local
    ./scripts/push_scripts/push-image-to-remote.sh "$REMOTE_URI" "$REMOTE_CERT_FILE" "$REMOTE_COMMAND $FLAVOR"
elif [ "$HOST_TYPE" = "local" ]; then
    echo "build on local"
    # 在本地构建，本地启动
    ./scripts/build_scripts/build-server-on-condition.sh "$FLAVOR" prod local
    ./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod local
    ./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'
else
    echo "build on remote"
    # 在远程主机上启动
    if [ -z "$LOAD_IMAGE" ]; then
        docker load -i "/tmp/A/$FLAVOR.image.tar"
    else
        ./scripts/build_scripts/build-server-image.sh "$FLAVOR" prod
    fi
    # 使用预构建镜像构建服务
    ./scripts/service_scripts/compose-service.sh "$FLAVOR" true 'up -d --build'
fi
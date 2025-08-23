#!/bin/bash
echo "start on remote"
docker load -i "/tmp/A/$FLAVOR.image.tar"
# 使用预构建镜像构建服务
export BUILD_ON=local
./scripts/service_scripts/compose-service.sh "$FLAVOR" true 'up -d --build'
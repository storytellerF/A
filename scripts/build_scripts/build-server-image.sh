#!/bin/sh
# 定义要保存的文件名
FILE="build/images/$FLAVOR.image.tar"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
  ./scripts/build_scripts/build-all-in-flavor.sh "$FLAVOR" true
  ./scripts/tool_scripts/exec-until-success.sh docker build --platform linux/amd64 --build-arg IS_PROD=$IS_PROD --build-arg FLAVOR=$FLAVOR --build-arg BUILD_ON=$BUILD_ON -t a-server:latest .
else
  echo "$FILE already exists. Skipping docker image build."
fi
#!/bin/sh

set -e
BUILD_ON=$1

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
  exit 1
fi

# 定义要保存的文件名
FILE="build/images/A.image.tar"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
  docker build --platform linux/amd64 \
    --build-arg BUILD_ON="$BUILD_ON" \
    -t a-server:latest .
else
  echo "$FILE already exists. Skipping docker image build."
fi
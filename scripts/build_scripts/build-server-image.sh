#!/bin/sh

set -e
FLAVOR=$1
BUILD_TYPE=$2
BUILD_ON=$3

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

if [ -z "$BUILD_TYPE" ]; then
  echo "BUILD_TYPE must be set"
  exit 1
fi

if [ -z "$BUILD_ON" ]; then
  echo "BUILD_ON must be set"
  exit 1
fi

# 定义要保存的文件名
FILE="build/images/$FLAVOR.image.tar"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
  docker build --platform linux/amd64 \
    --build-arg BUILD_TYPE="$BUILD_TYPE" \
    --build-arg FLAVOR="$FLAVOR" \
    --build-arg BUILD_ON="$BUILD_ON" \
    -t a-server:latest .
else
  echo "$FILE already exists. Skipping docker image build."
fi
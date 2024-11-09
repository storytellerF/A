#!/bin/bash
set -e
PUSH_TO_REMOTE_URI=$1
REMOTE_CERT_FILE=$2
REMOTE_COMMAND=$3
if [ -z "$PUSH_TO_REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ]; then
  echo "PUSH_TO_REMOTE_URI and REMOTE_CERT_FILE must be set"
  exit 1
fi

# 定义要保存的文件名
FILE="build/images/$FLAVOR.image.tar"
mkdir -p build/images
IMAGE_NAME="a-server:latest"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
  echo "$FILE does not exist. Saving Docker image..."
  docker save -o "$FILE" "$IMAGE_NAME"
  echo "Docker image saved to $FILE."
else
  echo "$FILE already exists. Skipping docker save."
fi

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "mkdir -p a-server"

echo "put $FILE /tmp/A/$FLAVOR.image.tar" | sftp -i "$REMOTE_CERT_FILE" -P 422 "$PUSH_TO_REMOTE_URI"

md=$(md5sum $FILE)
ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "echo "d41d8cd98f00b204e9800998ecf8427e  /tmp/A/$FLAVOR.image.tar" | md5sum -c -"

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "$REMOTE_COMMAND"
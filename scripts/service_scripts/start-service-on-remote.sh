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

sleep 2

echo "put $FILE ./a-server/$FLAVOR.image.tar" | sftp -i "$REMOTE_CERT_FILE" -P 422 "$PUSH_TO_REMOTE_URI"

sleep 2

md=$(md5sum "$FILE" | awk '{print $1}')
ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "echo ""$md"  ".a-server//$FLAVOR.image.tar"" | md5sum -c -"

sleep 2

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "sudo -s mv ./a-server/* /tmp/A"

sleep 2

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$PUSH_TO_REMOTE_URI" "$REMOTE_COMMAND"
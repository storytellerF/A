#!/bin/bash
set -e
REMOTE_URI=$1
REMOTE_CERT_FILE=$2
REMOTE_COMMAND=$3
if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
  echo "REMOTE_URI, REMOTE_COMMAND and REMOTE_CERT_FILE must be set"
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

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$REMOTE_URI" "mkdir -p a-server && mkdir -p /tmp/A"
sleep 2

md=$(md5sum "$FILE" | awk '{print $1}')
mdRemote=$(ssh -i ~/Downloads/default.pem -p 422 ubuntu@54.153.231.70 "md5sum ./a-server/$FLAVOR.image.tar | awk '{print \$1}'")
echo "local: $md remote: $mdRemote"
if [ "$md" != "$mdRemote" ]; then
  echo "put $FILE ./a-server/$FLAVOR.image.tar" | sftp -i "$REMOTE_CERT_FILE" -P 422 "$REMOTE_URI"
  sleep 2

  # 验证上传的文件完整性
  ssh -i "$REMOTE_CERT_FILE" -p 422 "$REMOTE_URI" "echo ""$md"  "./a-server/$FLAVOR.image.tar"" | md5sum -c -"
  sleep 2

  ./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$REMOTE_URI" "cp ./a-server/$FLAVOR.image.tar /tmp/A/$FLAVOR.image.tar"
  sleep 2

  # 验证上传的文件完整性
  ssh -i "$REMOTE_CERT_FILE" -p 422 "$REMOTE_URI" "echo ""$md"  "/tmp/A/$FLAVOR.image.tar"" | md5sum -c -"
  sleep 2
else
  echo "docker image same, skip upload."
fi

./scripts/tool_scripts/exec-until-success.sh ssh -i "$REMOTE_CERT_FILE" -p 422 "$REMOTE_URI" "$REMOTE_COMMAND"
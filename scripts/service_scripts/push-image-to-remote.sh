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
  echo "$FILE does not exist. Saving Docker image...`date`"
  docker save -o "$FILE" "$IMAGE_NAME"
  echo "Docker image saved to $FILE.`date`"
else
  echo "$FILE already exists. Skipping docker save.`date`"
fi

./scripts/tool_scripts/exec-until-success.sh ssh "$REMOTE_URI" "mkdir -p a-server"
sleep 2
./scripts/tool_scripts/exec-until-success.sh ssh "$REMOTE_URI" "mkdir -p /tmp/A"
sleep 2

md=$(md5sum "$FILE" | awk '{print $1}')
mdRemote=$(ssh "$REMOTE_URI" "md5sum ./a-server/$FLAVOR.image.tar | awk '{print \$1}'")
echo "local: $md remote: $mdRemote"
if [ "$md" != "$mdRemote" ]; then
  echo "upload tar `date`"
  echo "put $FILE ./a-server/$FLAVOR.image.tar" | sftp "$REMOTE_URI"
  sleep 2

  # 验证上传的文件完整性
  ssh "$REMOTE_URI" "echo ""$md"  "./a-server/$FLAVOR.image.tar"" | md5sum -c -"
  sleep 2

  ./scripts/tool_scripts/exec-until-success.sh ssh "$REMOTE_URI" "cp ./a-server/$FLAVOR.image.tar /tmp/A/$FLAVOR.image.tar"
  sleep 2

  # 验证上传的文件完整性
  ssh "$REMOTE_URI" "echo ""$md"  "/tmp/A/$FLAVOR.image.tar"" | md5sum -c -"
  sleep 2
else
  echo "docker image same, skip upload.`date`"
fi

echo "start docker `date`"
./scripts/tool_scripts/exec-until-success.sh ssh "$REMOTE_URI" "$REMOTE_COMMAND"
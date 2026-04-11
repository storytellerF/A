#!/bin/bash

. ./scripts/tool_scripts/terminal-log.sh

set -e

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  log "FLAVOR must be set"
  exit 1
fi

execute_ssh() {
  ssh default "$1"
  sleep 2
}

# 定义要保存的文件名
FILE="build/images/$FLAVOR.image.tar"
mkdir -p build/images
IMAGE_NAME="a-server:latest"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
  log "$FILE does not exist. Saving Docker image..."
  docker save -o "$FILE" "$IMAGE_NAME"
  FILE="$FILE.bz2"
  if [ -f "$FILE" ]; then
    log "$FILE already exists. Skipping docker save."
  else
    bzip2 "$FILE"
    log "Docker image saved to $FILE."
  fi
  log "Docker image saved to $FILE."
else
  log "$FILE already exists. Skipping docker save."
fi

execute_ssh "mkdir -p a-server"
execute_ssh "mkdir -p /tmp/A"

TARGET="./a-server/$FLAVOR.image.tar.bz2"
TARGET2="/tmp/A/$FLAVOR.image.tar.bz2"
md=$(md5sum "$FILE" | awk '{print $1}')
mdRemote=$(execute_ssh "md5sum "$TARGET" | awk '{print \$1}'")
log "local: $md remote: $mdRemote"
if [ "$md" != "$mdRemote" ]; then
  echo "put $FILE $TARGET" | sftp default
  log "tar $TARGET uploaded."
  # 验证上传的文件完整性
  execute_ssh "echo ""$md"  "$TARGET"" | md5sum -c -"
  log "tar $TARGET verified."
  execute_ssh "cp "$TARGET" "$TARGET2""
  log "tar copied to $TARGET2."
  # 解压
  execute_ssh "cd /tmp/A && bzip2 -df $TARGET2"
  log "tar $TARGET2 decompressed."
else
  log "docker image same, skip upload."
fi

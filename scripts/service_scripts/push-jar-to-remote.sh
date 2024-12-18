#!/bin/bash
set -e
REMOTE_URI=$1
REMOTE_CERT_FILE=$2
REMOTE_COMMAND=$3
if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
  echo "REMOTE_URI, REMOTE_COMMAND and REMOTE_CERT_FILE must be set"
  exit 1
fi

ssh "$REMOTE_URI" "mkdir -p a-server"
sleep 2

# 定义要保存的文件名
SERVER_JAR_FILE="./server/build/libs/server-all.jar"
SERVER_JAR_TARGET="./a-server/$FLAVOR/server-all.jar"

md=$(md5sum "$SERVER_JAR_FILE" | awk '{print $1}')
mdRemote=$(ssh "$REMOTE_URI" "md5sum "$SERVER_JAR_TARGET" | awk '{print \$1}'")
echo "local: $md remote: $mdRemote"
if [ "$md" != "$mdRemote" ]; then
  echo "upload $SERVER_JAR_FILE `date`"
  echo "put $SERVER_JAR_FILE $SERVER_JAR_TARGET" | sftp "$REMOTE_URI"
  echo "upload done `date`"
  sleep 2

  # 验证上传的文件完整性
  ssh "$REMOTE_URI" "echo ""$md"  "$SERVER_JAR_TARGET"" | md5sum -c -"
  sleep 2
else
  echo "skip upload $SERVER_JAR_FILE"
fi

echo "start docker `date`"
ssh "$REMOTE_URI" "$REMOTE_COMMAND"
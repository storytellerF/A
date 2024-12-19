#!/bin/bash
set -e
REMOTE_URI=$1
REMOTE_COMMAND=$2
if [ -z "$REMOTE_URI" ] || [ -z "$REMOTE_COMMAND" ]; then
  echo "REMOTE_URI, REMOTE_COMMAND and REMOTE_CERT_FILE must be set"
  exit 1
fi

if [ -z "$FLAVOR" ] ; then
  echo "FLAVOR must be set"
  exit 1
fi

ssh "$REMOTE_URI" "mkdir -p a-server/$FLAVOR"
sleep 2

# 定义文件数组
SERVER_JAR_FILES=(
  "./server/build/libs/server-all.jar"
  "./cli/build/distributions/cli.tar"
)

SERVER_JAR_TARGETS=(
  "./a-server/$FLAVOR/server-all.jar"
  "./a-server/$FLAVOR/cli.tar"
)

# 检查数组长度是否一致
if [ ${#SERVER_JAR_FILES[@]} -ne ${#SERVER_JAR_TARGETS[@]} ]; then
  echo "Error: The number of source files and target files do not match."
  exit 1
fi

# 遍历数组
for i in "${!SERVER_JAR_FILES[@]}"; do
  SERVER_JAR_FILE="${SERVER_JAR_FILES[$i]}"
  SERVER_JAR_TARGET="${SERVER_JAR_TARGETS[$i]}"

  # 计算本地和远程文件的 MD5
  md=$(md5sum "$SERVER_JAR_FILE" | awk '{print $1}')
  mdRemote=$(ssh "$REMOTE_URI" "md5sum \"$SERVER_JAR_TARGET\" | awk '{print \$1}'" 2>/dev/null || echo "")

  echo "local: $md remote: $mdRemote"
  if [ "$md" != "$mdRemote" ]; then
    echo "upload $SERVER_JAR_FILE $(date)"
    bzip2 -k $SERVER_JAR_FILE
    echo "put $SERVER_JAR_FILE.bz2 $SERVER_JAR_TARGET.bz2" | sftp "$REMOTE_URI"
    echo "upload done $(date)"
    sleep 2

    ssh "$REMOTE_URI" "bzip2 -kdf $SERVER_JAR_TARGET.bz2"
    sleep 2
    # 验证上传的文件完整性
    ssh "$REMOTE_URI" "echo \"$md  $SERVER_JAR_TARGET\" | md5sum -c -" || {
      echo "Remote file integrity check failed for $SERVER_JAR_TARGET"
      exit 1
    }
    sleep 2
  else
    echo "skip upload $SERVER_JAR_FILE"
  fi
done


echo "start docker `date`"
ssh "$REMOTE_URI" "$REMOTE_COMMAND"
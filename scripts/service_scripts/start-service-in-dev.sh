#!/usr/bin/env bash
FLAVOR=dev
# 检测操作系统
if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  FLAVOR=dev.win
fi

# 获取操作系统类型
OS_NAME=$(uname | tr '[:upper:]' '[:lower:]')

if [[ "$OS_NAME" == "linux" || "$OS_NAME" == "darwin" ]]; then
    LOG_PATH="/var/logs"
elif [[ "$OS_NAME" == *"mingw"* || "$OS_NAME" == *"cygwin"* ]]; then
    # Windows 下 bash (Git Bash / Cygwin)
    LOG_PATH=$(java -XshowSettings:properties -version 2>&1 | grep 'java.io.tmpdir' | awk -F= '{gsub(/ /,"",$2); print $2}')
else
    # 默认使用 tmp
    LOG_PATH=$(java -XshowSettings:properties -version 2>&1 | grep 'java.io.tmpdir' | awk -F= '{gsub(/ /,"",$2); print $2}')
fi

export LOG_PATH
echo "LOG_PATH set to: $LOG_PATH"

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" dev
./scripts/service_scripts/compose-service.sh "$FLAVOR" false 'up -d --build'
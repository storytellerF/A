#!/usr/bin/env bash

# 获取操作系统类型
OS_NAME=$(uname | tr '[:upper:]' '[:lower:]')

if [[ "$OS_NAME" == "linux" || "$OS_NAME" == "darwin" ]]; then
    LOG_PATH="/tmp/a-log"
elif [[ "$OS_NAME" == *"mingw"* || "$OS_NAME" == *"cygwin"* ]]; then
    # Windows 下 bash (Git Bash / Cygwin)
    LOG_PATH=$(java -XshowSettings:properties -version 2>&1 | grep 'java.io.tmpdir' | awk -F= '{gsub(/ /,"",$2); print $2}')
else
    # 默认使用 tmp
    LOG_PATH=$(java -XshowSettings:properties -version 2>&1 | grep 'java.io.tmpdir' | awk -F= '{gsub(/ /,"",$2); print $2}')
fi

export LOG_PATH
echo "LOG_PATH set to: $LOG_PATH"

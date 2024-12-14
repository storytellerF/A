#!/bin/bash
# 检查是否传递了命令作为参数
if [ -z "$1" ]; then
  echo "Error: No command provided."
  exit 1
fi

# 将所有的参数当做要执行的命令
COMMAND="$@"

# 设置最大重试次数
MAX_RETRIES=5
RETRY_COUNT=0

# 无限循环执行命令直到成功或达到最大重试次数
while true; do
  # 执行命令
  eval "$COMMAND"

  result=$?
  # 检查命令执行是否成功（返回码为0表示成功）
  if [ $result -eq 0 ]; then
    echo "Command succeeded."
    break
  else
    # 增加重试次数 bash 特有
    RETRY_COUNT=$((RETRY_COUNT + 1))

    # 检查是否超过最大重试次数
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
      echo "Command failed [$result]. Reached maximum retry limit ($MAX_RETRIES)."
      exit 1
    else
      echo "Command failed [$result]. Retrying ($RETRY_COUNT/$MAX_RETRIES)..."
      sleep 1  # 等待一秒再重试，避免过快的重试
    fi
  fi
done

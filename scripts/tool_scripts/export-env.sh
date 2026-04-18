#!/usr/bin/env bash

# 读取dev.env 文件并导出环境变量
if [ -f "deploy/dev.env" ]; then
  echo "Loading environment variables from deploy/dev.env"
  while IFS='=' read -r key value; do
    # 跳过注释和空行
    [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
    export "$key=$value"
  done < deploy/dev.env
else
  echo "Warning: dev.env file not found. Skipping environment variable setup."
fi
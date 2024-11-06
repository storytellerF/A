#!/bin/bash

set -e

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set."
  exit 1
fi

# 定义变量
REPO="storytellerF/A" # 替换为你的仓库
ENVIRONMENT="$FLAVOR" # 替换为你指定的环境
ENV_FILE="$FLAVOR.env"

# 检查 gh 命令是否存在
if ! command -v gh &>/dev/null; then
  echo "Error: gh command not found."
  exit 1
fi

if [ "$(gh api \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "/repos/storytellerF/A/environments/$FLAVOR" \
  -q ".name")" != "$ENVIRONMENT" ]; then

  echo "Environment '$ENVIRONMENT' does not exist."
  exit 1
fi

# 读取 .env 文件并处理
while IFS= read -r line; do
  # 处理换行符
  line=$(echo "$line" | tr -d '\r')
  # 获取 key 和 value
  key=$(echo "$line" | cut -d '=' -f 1)
  value=$(echo "$line" | cut -d '=' -f 2-)
  [[ -z "$key" || "$key" == \#* ]] && continue
  if [ -z "$value" ]; then
    echo "$key is empty, skip."
    continue
  fi

  if [[ "$key" == *"KEY"* || "$key" == *"PASS"* || "$key" == *"PASSWORD"* ]]; then
    # 保存到 secret
    ./scripts/tool_scripts/exec-until-success.sh gh secret set "$key" -b "$value" -R "$REPO" -e "$ENVIRONMENT"
  else
    # 保存到 variable
    ./scripts/tool_scripts/exec-until-success.sh gh variable set "$key" -b "$value" -R "$REPO" -e "$ENVIRONMENT"
  fi

  sleep 2
done <"$ENV_FILE"

echo "Environment variables processed."

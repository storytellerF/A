#!/bin/bash

set -e

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set."
  exit 1
fi

# Variables
OWNER="storytellerF"       # 替换为仓库的拥有者
REPO="A"                       # 替换为你的仓库名
ENVIRONMENT="$FLAVOR"          # 替换为目标环境名
ENV_FILE="$FLAVOR.env"                       # .env 文件路径
PUSH_TOKEN_KEY="PUSH_TOKEN"
PUSH_TOKEN=$(grep "^${PUSH_TOKEN_KEY}=" "$ENV_FILE" | cut -d '=' -f2-) # 替换为你的 GitHub 访问令牌

if [ -z "$PUSH_TOKEN" ]; then
  echo "PUSH_TOKEN must be set."
  exit 1
fi

check_and_set_variable() {
  # 接收一个参数作为环境变量的键
  local env_key="$1"

  # 定义敏感关键词
  local keywords=("TOKEN" "PASS" "PASSWORD" "KEY")

  # 检查环境变量键是否包含任何敏感关键字
  for keyword in "${keywords[@]}"; do
    if [[ $env_key == *"$keyword"* ]]; then
      echo "环境变量键 '$env_key' 包含敏感关键字 '$keyword'。"
      return 1  # 返回 1
    fi
  done

  # 如果没有敏感关键字，返回 0
  echo "环境变量键 '$env_key' 不包含敏感关键字。"
  return 0  # 返回 0
}

# Function to check if the environment exists
check_environment_exists() {
    local env_name="$1"
    response=$(curl -fL \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer $PUSH_TOKEN" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "https://api.github.com/repos/$OWNER/$REPO/environments")
    echo "$response" | jq -e ".environments[] | select(.name == \"$env_name\")" > /dev/null
}

# Check if the environment exists
if check_environment_exists "$ENVIRONMENT"; then
  echo "Environment $ENVIRONMENT exists. Proceeding to add secret..."
else
  echo "Environment $ENVIRONMENT does not exist. Skipping secrets."
  exit 1
fi

# Step 1: Get the public key for the environment
PUBLIC_KEY_INFO=$(curl -fsL \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer $PUSH_TOKEN" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/$OWNER/$REPO/environments/$ENVIRONMENT/secrets/public-key")

PUBLIC_KEY=$(echo "$PUBLIC_KEY_INFO" | jq -r .key)
KEY_ID=$(echo "$PUBLIC_KEY_INFO" | jq -r .key_id)

echo "$PUBLIC_KEY $KEY_ID"

# Read the .env file
while IFS='=' read -r secret_name secret_value; do
    # Skip empty lines and comments
    [[ -z "$secret_name" || "$secret_name" == \#* ]] && continue
    if [ -z "$secret_value" ]; then
      echo "$secret_name is empty, skip."
      continue
    fi

    check_and_set_variable $secret_name
    result=$?

    # 检查返回结果
    if [ $result -eq 0 ]; then
        exists_name=$(curl -sL \
                   -H "Accept: application/vnd.github+json" \
                   -H "Authorization: Bearer $PUSH_TOKEN" \
                   -H "X-GitHub-Api-Version: 2022-11-28" \
                   "https://api.github.com/repos/$OWNER/$REPO/environments/$ENVIRONMENT/variables/$secret_name" | jq -r '.name')
        if [ "$exists_name" = "$secret_name" ]; then

            curl -L \
                -X PATCH \
                -H "Accept: application/vnd.github+json" \
                -H "Authorization: Bearer $PUSH_TOKEN" \
                -H "X-GitHub-Api-Version: 2022-11-28" \
                -d "{\"name\":\"$secret_name\",\"value\":\"$secret_value\"}" \
                "https://api.github.com/repos/$OWNER/$REPO/environments/$ENVIRONMENT/variables/$secret_name"
        else
            curl -L \
                -X POST \
                -H "Accept: application/vnd.github+json" \
                -H "Authorization: Bearer $PUSH_TOKEN" \
                -H "X-GitHub-Api-Version: 2022-11-28" \
                -d "{\"name\":\"$secret_name\",\"value\":\"$secret_value\"}" \
                "https://api.github.com/repos/$OWNER/$REPO/environments/$ENVIRONMENT/variables"
        fi
    else
        ENCRYPTED_SECRET=$(node scripts/tool_scripts/encrypt-secret.js $PUBLIC_KEY "$secret_value")

        curl -L -X PUT \
            -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $PUSH_TOKEN" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            -d "{\"encrypted_value\":\"$ENCRYPTED_SECRET\",\"key_id\":\"$KEY_ID\"}" \
            "https://api.github.com/repos/$OWNER/$REPO/environments/$ENVIRONMENT/secrets/$secret_name"

        echo "Secret $secret_name has been added to environment $ENVIRONMENT."
    fi

    sleep 2

done < "$ENV_FILE"


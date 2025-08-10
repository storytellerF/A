#!/bin/bash

set -e

echo "$VARS_CONTEXT" | jq -r 'to_entries | .[] | "\(.key)=\(.value)"' | while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    export "$key"="$value"
done

echo "$SECRETS_CONTEXT" | jq -r 'to_entries | .[] | "\(.key)=\(.value)"' | while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    export "$key"="$value"
done

if [ "$(uname)" = "Darwin" ]; then
  echo "$REMOTE_ENCODED_CERT" | base64 --decode -o remote.pem
else
  echo "$REMOTE_ENCODED_CERT" | base64 --decode > remote.pem
fi

chmod 600 ./remote.pem

echo "prepare connect remote $(date)"
# 检查 known_hosts 文件是否存在
if [ ! -f ~/.ssh/known_hosts ]; then
  # 如果文件不存在，创建空的 known_hosts 文件
  mkdir -p ~/.ssh
  touch ~/.ssh/known_hosts
fi

ssh-keyscan -H acommunity.link >> ~/.ssh/known_hosts

eval $(ssh-agent)

ssh-add ./remote.pem

./scripts/service-scripts/start-service-in-remote-by-jar.sh "$FLAVOR" local ubuntu@acommunity.link
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

mkdir -p ~/.ssh

if [ "$(uname)" = "Darwin" ]; then
  echo "$SSH_KEY" | base64 --decode -o ~/.ssh/id_ed25519
else
  echo "$SSH_KEY" | base64 --decode > ~/.ssh/id_ed25519
fi

chmod 600 ~/.ssh/id_ed25519

# 检查 known_hosts 文件是否存在
if [ ! -f ~/.ssh/known_hosts ]; then
  # 如果文件不存在，创建空的 known_hosts 文件
  echo "Creating ~/.ssh/known_hosts file..."
  mkdir -p ~/.ssh
  touch ~/.ssh/known_hosts
fi

# Extract host and port from SSH_URI
SSH_HOST=$(echo "$SSH_URI" | sed 's|ssh://||' | cut -d':' -f1)
SSH_PORT=$(echo "$SSH_URI" | sed 's|ssh://||' | cut -d':' -f2)

ssh-keyscan -p "$SSH_PORT" -H "$SSH_HOST" >> ~/.ssh/known_hosts

# eval "$(ssh-agent)"

# ssh-add ~/.ssh/remote.pem

./scripts/service-scripts/start-service-in-remote-by-image.sh "$FLAVOR" local ubuntu@$SSH_URI

rm -rf ~/.ssh
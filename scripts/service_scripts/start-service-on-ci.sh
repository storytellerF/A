#!/bin/bash

. ./scripts/tool_scripts/terminal-log.sh

set -e

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  log "FLAVOR must be set"
  exit 1
fi

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
  log "Creating ~/.ssh/known_hosts file."
  mkdir -p ~/.ssh
  touch ~/.ssh/known_hosts
else
  log "Known_hosts file already exists."
fi

# Extract host and port from SSH_URI ssh://user@host:port
SSH_USER=$(echo "$SSH_URI" | sed 's|ssh://||' | cut -d'@' -f1)
host_part=$(echo "$SSH_URI" | sed 's|ssh://||' | cut -d'@' -f2)
SSH_HOST=$(echo "$host_part" | cut -d':' -f1)
SSH_PORT=$(echo "$host_part" | cut -d':' -f2)

ssh-keyscan -p "$SSH_PORT" -T 3600 -H "$SSH_HOST" >> ~/.ssh/known_hosts 2>/dev/null

# 如果 ssh config 不包含 default 主机，则创建它
if ! grep -q "Host default" ~/.ssh/config 2>/dev/null; then
  log "create ssh config"
  echo "Host default" >> ~/.ssh/config
  echo "  HostName $SSH_HOST" >> ~/.ssh/config
  echo "  Port $SSH_PORT" >> ~/.ssh/config
  echo "  User $SSH_USER" >> ~/.ssh/config
  echo "  IdentityFile ~/.ssh/id_ed25519" >> ~/.ssh/config
fi

export HOST_TYPE=local
# ./scripts/service_scripts/start-service-in-remote-by-image.sh "$FLAVOR" local
ssh default "cd Projects/AData && git pull && cd ../A && git stash && git fetch && git reset --hard origin/alpha && tmux new-session -d -s A './scripts/service_scripts/start-service-in-local.sh $FLAVOR'"

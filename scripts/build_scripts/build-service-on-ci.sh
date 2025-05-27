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

echo "prepare connect remote `date`"
# 检查 known_hosts 文件是否存在
if [ ! -f ~/.ssh/known_hosts ]; then
  # 如果文件不存在，创建空的 known_hosts 文件
  mkdir -p ~/.ssh
  touch ~/.ssh/known_hosts
fi

ssh-keyscan -H acommunity.link >> ~/.ssh/known_hosts

eval $(ssh-agent)

ssh-add ./remote.pem

# 检查远端是否可用
remoteName=$(ssh ubuntu@acommunity.link "whoami")
if [ "$remoteName" != 'ubuntu' ]; then
    echo "connect remote failed"
    exit 1
fi

echo "prepare build `date`"

# 构建远端
#echo "build docker image `date`"
#HOST_TYPE=local ./scripts/build_scripts/build-server-image.sh
HOST_TYPE=local ./scripts/build_scripts/build-server-on-condition.sh "$FLAVOR" prod local
echo "push to remote `date`"
# 远端没有生成的env 文件，需要使用原始env 文件
./scripts/push_scripts/push-jar-to-remote.sh ubuntu@acommunity.link "./start-jar-service-in-ubuntu.sh $FLAVOR"
echo "done `date`"
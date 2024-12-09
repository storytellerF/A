#!/bin/bash

set -e

TEMP_FILE=./temp

# Pipe the JSON string into jq
echo "$VARS_CONTEXT" | 
# Convert JSON object into an array of key-value pairs
jq -r 'to_entries | 
# Map over each key-value pair
.[] | 
# Format each pair as "KEY=VALUE" and append it all to the environment file
"\(.key)=\(.value)"' >> $TEMP_FILE

# Pipe the JSON string into jq
echo "$SECRETS_CONTEXT" | 
# Convert JSON object into an array of key-value pairs
jq -r 'to_entries | 
# Map over each key-value pair
.[] | 
# Format each pair as "KEY=VALUE" and append it all to the environment file
"\(.key)=\(.value)"' >> $TEMP_FILE

while IFS= read -r line; do
    # Ignore empty lines and comments
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    IFS='=' read -r key value <<< "$line"
    export "$key"="$value"
done < $TEMP_FILE

if [ "$(uname)" = "Darwin" ]; then
  echo "$REMOTE_ENCODED_CERT" | base64 --decode -o remote.pem
else
  echo "$REMOTE_ENCODED_CERT" | base64 --decode > remote.pem
fi

chmod 600 ./remote.pem

# 检查 known_hosts 文件是否存在
if [ ! -f ~/.ssh/known_hosts ]; then
  # 如果文件不存在，创建空的 known_hosts 文件
  mkdir -p ~/.ssh
  touch ~/.ssh/known_hosts
fi

ssh-keyscan -H acommunity.link >> ~/.ssh/known_hosts

cat ~/.ssh/known_hosts

eval $(ssh-agent)

ssh-add ./remote.pem

# 检查远端是否可用
ssh ubuntu@acommunity.link "whoami"

./scripts/tool_scripts/save-env.sh

./scripts/tool_scripts/modify-flavor.sh "$FLAVOR" true

./gradlew composeApp:build

mkdir -p "build/outputs/apk/release"

mv composeApp/build/outputs/apk/release/*.apk "build/outputs/apk/release/$FLAVOR.apk"

# 构建远端
echo "build docker image"
HOST_TYPE=local ./scripts/build_scripts/build-server-image.sh
echo "push to remote"
# 远端没有生成的env 文件，需要使用原始env 文件
./scripts/service_scripts/push-image-to-remote.sh ubuntu@acommunity.link ./remote.pem "sudo bash ./start.sh $FLAVOR"

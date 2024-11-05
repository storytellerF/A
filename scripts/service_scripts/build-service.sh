#!/bin/bash
set -e

if [ -z "$IS_LOCAL_HOST" ] || [ -z "$IS_REMOTE_HOST" ]; then
  echo "IS_LOCAL_HOST/IS_REMOTE_HOST must be set."
  exit 1
fi

FLAVOR=$1

if [ -z "$FLAVOR" ]; then
  echo "FLAVOR must be set"
  exit 1
fi

PUSH_TO_REMOTE_URI=$2
REMOTE_CERT_FILE=$3
REMOTE_COMMAND=$4

while IFS= read -r line; do
  # Ignore empty lines and comments
  [[ -z "$line" || "$line" =~ ^# ]] && continue
  IFS='=' read -r key value <<<"$line"
  export "$key"="$value"
done <"$FLAVOR.env"

if [ -z "$PUSH_TO_REMOTE_URI" ] || [ -z "$REMOTE_CERT_FILE" ] || [ -z "$REMOTE_COMMAND" ]; then
  if [ "$IS_LOCAL_HOST" = "true" ]; then
    # 在本地启动
    IS_DOCKER=false \
      ./scripts/build_scripts/build-all-in-flavor.sh "$FLAVOR" true
    "./scripts/service_scripts/start-$FLAVOR-compose.sh"
  else
    # 在远程主机上启动
    # load image
    docker load -i "/tmp/A/$FLAVOR.image.tar"
    # 使用预构建镜像构建服务
    "./scripts/service_scripts/start-$FLAVOR-compose.sh" true
  fi

else
  IS_DOCKER=false \
    ./scripts/build_scripts/build-all-in-flavor.sh "$FLAVOR" true
  args=$(grep -v '^#' "$FLAVOR".env | grep -v '^$' | awk -F '=' '{print "--build-arg " $1 "=\"" $2 "\""}' ORS=' ')
  ./scripts/tool_scripts/exec-until-success.sh docker build "$args" -t "a-server:latest" .
  ./scripts/service_scripts/start-service-on-remote.sh "$PUSH_TO_REMOTE_URI" "$REMOTE_CERT_FILE" "$REMOTE_COMMAND $FLAVOR"
fi

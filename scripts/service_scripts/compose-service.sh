#!/bin/bash

FLAVOR=$1
USE_PREBUILD=$2
CUSTOM_COMMAND=$3

if [ -z "$FLAVOR" ]; then
    echo "FLAVOR must be set."
    exit 1
fi

# 默认 compose 命令
if [ -z "$CUSTOM_COMMAND" ]; then
    CUSTOM_COMMAND="up -d --build"
fi

COMPOSE_FILE_LIST=()
case "$FLAVOR" in
  mini)
    export COMPOSE_PROFILES="pg,server"
    COMPOSE_FILE_LIST=("pg" "server" "p")
    ;;
  dev)
    export COMPOSE_PROFILES="pg,elastic,minio"
    COMPOSE_FILE_LIST=("pg" "elastic" "minio" "pem" "certs_bind")
    ;;
  dev.win)
    export COMPOSE_PROFILES="pg,elastic,minio"
    COMPOSE_FILE_LIST=("pg" "elastic" "minio" "pem")
    ;;
  *)
    echo "未知环境：$FLAVOR"
    exit 1
    ;;
esac


# 公共 compose 文件
COMPOSE_FILES=("-f" "./deploy/docker-compose/docker-compose.yml")

# 条件：USE_PREBUILD = true
if [ "$USE_PREBUILD" = "true" ]; then
    COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.prebuild.yml")
fi

for profile in "${COMPOSE_FILE_LIST[@]}"; do
    profile=$(echo "$profile" | xargs)
    COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.${profile}.yml")
done

IFS=' ' read -r -a custom_cmd_parts <<< "$CUSTOM_COMMAND"
CMD=("docker" "compose" "--env-file" "./${FLAVOR}.env" "${COMPOSE_FILES[@]}" "${custom_cmd_parts[@]}")

echo "Executing: ${CMD[@]}"
"${CMD[@]}"

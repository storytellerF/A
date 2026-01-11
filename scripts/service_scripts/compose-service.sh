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

IFS=',' read -ra COMPOSE_FILE_LIST <<< "$(grep '^COMPOSE_FILE_LIST=' "./deploy/$FLAVOR.env" | cut -d '=' -f2-)"
echo "${COMPOSE_FILE_LIST[@]}"

# 公共 compose 文件
COMPOSE_FILES=("-f" "./deploy/docker-compose/docker-compose.yml")

for profile in "${COMPOSE_FILE_LIST[@]}"; do
    profile=$(echo "$profile" | xargs)
    COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.${profile}.yml")
done

IFS=' ' read -r -a custom_cmd_parts <<< "$CUSTOM_COMMAND"

# 条件：USE_PREBUILD = true
if [ "$USE_PREBUILD" = "true" ]; then
    COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.prebuild.yml")
else
  for p in "${COMPOSE_FILE_LIST[@]}"; do
      if [[ "$p" == "server" ]]; then
          if [ -z "$BUILD_ON" ]; then
              echo "BUILD_ON must be set."
              exit 1
          fi
	  export BUILD_ON=$BUILD_ON
          break
      fi
  done
fi

CMD=("docker" "compose" "--env-file" "./deploy/${FLAVOR}.env" "${COMPOSE_FILES[@]}" "${custom_cmd_parts[@]}")

echo "Executing: ${CMD[@]}"
"${CMD[@]}"

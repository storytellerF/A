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
NEEDS_BUILD_ON=false
ENABLED_PROFILES=()
GENERATED_ONLY_PROFILES=("bunker")

has_profile() {
    local target=$1
    local profile
    for profile in "${ENABLED_PROFILES[@]}"; do
        if [[ "$profile" == "$target" ]]; then
            return 0
        fi
    done
    return 1
}

is_generated_only_profile() {
    local target=$1
    local profile
    for profile in "${GENERATED_ONLY_PROFILES[@]}"; do
        if [[ "$profile" == "$target" ]]; then
            return 0
        fi
    done
    return 1
}

emit_bunker_network_if_needed() {
    if has_profile bunker; then
        echo "    networks:"
        echo "      - bw-services"
    fi
}

for profile in "${COMPOSE_FILE_LIST[@]}"; do
    profile=$(echo "$profile" | xargs)
    if [ -z "$profile" ]; then
        continue
    fi
    ENABLED_PROFILES+=("$profile")
    if [[ "$profile" == "server" ]] || [[ "$profile" == "worker" ]] || [[ "$profile" == "cli" ]]; then
        NEEDS_BUILD_ON=true
    fi
    if ! is_generated_only_profile "$profile"; then
        COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.${profile}.yml")
    fi
done

GENERATED_COMPOSE_FILE="./deploy/docker-compose/docker-compose.generated-patch.yml"
cleanup_generated_compose_file() {
    rm -f "$GENERATED_COMPOSE_FILE"
}
{
    if ! has_profile bunker && ! has_profile cli && ! has_profile server && ! has_profile worker; then
        echo "services: {}"
    else
        echo "services:"
        if has_profile bunker; then
            if has_profile pg; then
                echo "  pg:"
                emit_bunker_network_if_needed
                echo "  adminer:"
                emit_bunker_network_if_needed
                echo "  pg-exporter:"
                emit_bunker_network_if_needed
            fi
            if has_profile minio; then
                echo "  minio:"
                emit_bunker_network_if_needed
            fi
            if has_profile grafana; then
                echo "  grafana:"
                emit_bunker_network_if_needed
                echo "  prometheus:"
                emit_bunker_network_if_needed
                echo "  loki:"
                emit_bunker_network_if_needed
                echo "  promtail:"
                emit_bunker_network_if_needed
            fi
            if has_profile elastic; then
                echo "  setup:"
                emit_bunker_network_if_needed
                echo "  es01:"
                emit_bunker_network_if_needed
                echo "  kibana:"
                emit_bunker_network_if_needed
            fi
            if has_profile dicebear; then
                echo "  dicebear:"
                emit_bunker_network_if_needed
            fi
            if has_profile etcd; then
                echo "  etcd:"
                emit_bunker_network_if_needed
            fi
        fi
        if has_profile cli; then
            echo "  cli:"
            if has_profile pg || has_profile minio || has_profile elastic; then
                echo "    depends_on:"
                if has_profile pg; then
                    echo "      pg:"
                    echo "        condition: service_healthy"
                fi
                if has_profile minio; then
                    echo "      minio:"
                    echo "        condition: service_healthy"
                fi
                if has_profile elastic; then
                    echo "      es01:"
                    echo "        condition: service_healthy"
                fi
            else
                echo "    # no generated dependencies"
            fi
            echo "    volumes:"
            if has_profile elastic; then
                echo "      - certs:/app/deploy/es_ca"
            else
                echo "      - ../lucene_data:/app/deploy/lucene_data"
            fi
            if ! has_profile minio; then
                echo "      - ../a_file_data:/app/deploy/a_file_data"
            fi
            emit_bunker_network_if_needed
        fi
        for service in server worker; do
            if has_profile "$service"; then
                echo "  $service:"
                if has_profile cli; then
                    echo "    depends_on:"
                    echo "      cli:"
                    echo "        condition: service_healthy"
                    if has_profile pg; then
                        echo "      pg:"
                        echo "        condition: service_healthy"
                    fi
                    if has_profile minio; then
                        echo "      minio:"
                        echo "        condition: service_healthy"
                    fi
                    if has_profile elastic; then
                        echo "      es01:"
                        echo "        condition: service_healthy"
                    fi
                elif has_profile pg || has_profile minio || has_profile elastic; then
                    echo "    depends_on:"
                    if has_profile pg; then
                        echo "      pg:"
                        echo "        condition: service_healthy"
                    fi
                    if has_profile minio; then
                        echo "      minio:"
                        echo "        condition: service_healthy"
                    fi
                    if has_profile elastic; then
                        echo "      es01:"
                        echo "        condition: service_healthy"
                    fi
                fi
                emit_bunker_network_if_needed
                echo "    volumes:"
                if has_profile elastic; then
                    echo "      - certs:/app/deploy/es_ca"
                else
                    echo "      - ../lucene_data:/app/deploy/lucene_data"
                fi
                if ! has_profile minio; then
                    echo "      - ../a_file_data:/app/deploy/a_file_data"
                fi
            fi
        done
    fi
    if has_profile bunker; then
        echo "networks:"
        echo "  bw-services:"
        echo "    external: true"
        echo "    name: bw-services"
    fi
} > "$GENERATED_COMPOSE_FILE"
COMPOSE_FILES+=("-f" "$GENERATED_COMPOSE_FILE")

IFS=' ' read -r -a custom_cmd_parts <<< "$CUSTOM_COMMAND"

# 条件：USE_PREBUILD = true
if [ "$USE_PREBUILD" = "true" ]; then
    COMPOSE_FILES+=("-f" "./deploy/docker-compose/docker-compose.prebuild.yml")
else
  if [[ "$NEEDS_BUILD_ON" == "true" ]]; then
      if [ -z "$BUILD_ON" ]; then
          echo "BUILD_ON must be set."
          exit 1
      fi
      export BUILD_ON=$BUILD_ON
  fi
fi

CMD=("docker" "compose" "--env-file" "./deploy/${FLAVOR}.env" "${COMPOSE_FILES[@]}" "${custom_cmd_parts[@]}")

echo "Executing: ${CMD[@]}"
# "${CMD[@]}"

docker compose --env-file ./deploy/${FLAVOR}.env ${COMPOSE_FILES[@]} ${custom_cmd_parts[@]}

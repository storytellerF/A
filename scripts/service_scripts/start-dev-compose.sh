#!/bin/sh
set -e
CUSTOM_COMMAND=$1

if [ -z "$CUSTOM_COMMAND" ]; then
    CUSTOM_COMMAND="up -d --build"
fi

cd deploy
mkdir -p es_ca
export COMPOSE_BAKE=true
docker compose --env-file ../dev.env \
  -f deploy/docker-compose/docker-compose.yml \
  -f deploy/docker-compose/docker-compose.dem.yml \
  -f deploy/docker-compose/docker-compose.certs_volume.yml \
  -f deploy/docker-compose/docker-compose.pg.yml \
  -f deploy/docker-compose/docker-compose.elastic.yml \
  -f deploy/docker-compose/docker-compose.minio.yml \
  $CUSTOM_COMMAND
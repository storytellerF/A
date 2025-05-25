#!/bin/sh
set -e

cd deploy
mkdir -p es_ca
export COMPOSE_BAKE=true
docker compose --env-file ../prod.env \
  -f deploy/docker-compose/docker-compose.yml \
  -f deploy/docker-compose/docker-compose.dem.yml \
  -f deploy/docker-compose/docker-compose.pg.yml \
  -f deploy/docker-compose/docker-compose.elastic.yml \
  -f deploy/docker-compose/docker-compose.minio.yml \
  up -d --build
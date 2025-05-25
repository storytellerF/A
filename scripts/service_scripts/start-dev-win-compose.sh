#!/bin/sh
set -e

cd deploy
mkdir -p es_ca
docker compose --env-file ../dev.win.env \
  -f docker-compose/docker-compose.yml \
  -f docker-compose/docker-compose.dem.yml \
  -f docker-compose/docker-compose.pg.yml \
  -f docker-compose/docker-compose.elastic.yml \
  -f docker-compose/docker-compose.minio.yml \
  up -d --build
#!/bin/sh
set -e
CUSTOM_COMMAND=$1

if [ -z "$CUSTOM_COMMAND" ]; then
    CUSTOM_COMMAND="up -d --build"
fi

cd deploy
mkdir -p es_ca
docker compose --env-file ../dev.env \
  -f docker-compose.yml \
  -f docker-compose.dem.yml \
  -f docker-compose.b.yml \
  $CUSTOM_COMMAND
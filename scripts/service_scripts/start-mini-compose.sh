#!/bin/sh

USE_PREBUILD=$1
CUSTOM_COMMAND=$2

if [ -z "$CUSTOM_COMMAND" ]; then
    CUSTOM_COMMAND="up -d --build"
fi
export COMPOSE_BAKE=true
if [ "$USE_PREBUILD" = "true" ]; then
     docker compose --env-file ./mini.env \
          -f deploy/docker-compose/docker-compose.yml \
          -f deploy/docker-compose/docker-compose.d.yml \
          -f deploy/docker-compose/docker-compose.prebuild.yml \
          -f deploy/docker-compose/docker-compose.mini.yml \
          -f deploy/docker-compose/docker-compose.pg.yml \
          $CUSTOM_COMMAND
else
     docker compose --env-file ./mini.env \
          -f deploy/docker-compose/docker-compose.yml \
          -f deploy/docker-compose/docker-compose.d.yml \
          -f deploy/docker-compose/docker-compose.mini.yml \
          -f deploy/docker-compose/docker-compose.pg.yml \
          $CUSTOM_COMMAND
fi

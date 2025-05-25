#!/bin/sh

USE_PREBUILD=$1
CUSTOM_COMMAND=$2

if [ -z "$CUSTOM_COMMAND" ]; then
    CUSTOM_COMMAND="up -d --build"
fi

cd deploy

if [ "$USE_PREBUILD" = "true" ]; then
     docker compose --env-file ../mini.env \
          -f docker-compose/docker-compose.yml \
          -f docker-compose/docker-compose.d.yml \
          -f docker-compose/docker-compose.prebuild.yml \
          -f docker-compose/docker-compose.mini.yml \
          -f docker-compose/docker-compose.pg.yml \
          $CUSTOM_COMMAND
else
     docker compose --env-file ../mini.env \
          -f docker-compose/docker-compose.yml \
          -f docker-compose/docker-compose.d.yml \
          -f docker-compose/docker-compose.mini.yml \
          -f docker-compose/docker-compose.pg.yml \
          $CUSTOM_COMMAND
fi
